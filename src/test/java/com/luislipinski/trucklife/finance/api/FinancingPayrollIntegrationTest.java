package com.luislipinski.trucklife.finance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentType;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEventRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialContractRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialInstallmentRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialPaymentRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient @ActiveProfiles("test") @Testcontainers
class FinancingPayrollIntegrationTest {
    private static final String CAREERS="/api/v1/careers";
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired RestTestClient restTestClient;@Autowired UserRepository userRepository;@Autowired CareerRepository careerRepository;@Autowired FinancialContractRepository contractRepository;@Autowired FinancialInstallmentRepository installmentRepository;@Autowired FinancialPaymentRepository paymentRepository;@Autowired FinancialContractEventRepository eventRepository;@Autowired LedgerEntryRepository ledgerRepository;@Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach void clean(){ledgerRepository.deleteAllInBatch();eventRepository.deleteAllInBatch();paymentRepository.deleteAllInBatch();installmentRepository.deleteAllInBatch();contractRepository.deleteAllInBatch();careerRepository.deleteAllInBatch();userRepository.deleteAllInBatch();}

    @Test void atsPayslipAutomaticallyPaysDueWeeklyInstallmentInSameOperationalClose(){UserEntity owner=saveUser("financing-payroll@example.com");String token=tokenIssuer.issue(owner,UUID.randomUUID()).token();CareerResponse career=createCareer(token);Map<String,Object> contract=new LinkedHashMap<>();contract.put("operationId",UUID.randomUUID());contract.put("productType","PERSONAL_LOAN");contract.put("requestedAmount",new BigDecimal("500.00"));contract.put("termPeriods",52);contract.put("expectedOperationalWeek",1);contract.put("expectedBalance",new BigDecimal("5000.00"));FinancialContractResponse created=Objects.requireNonNull(restTestClient.post().uri(financingPath(career.id())).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(contract).exchange().expectStatus().isCreated().expectBody(FinancialContractResponse.class).returnResult().getResponseBody());BigDecimal originalPrincipal=created.remainingPrincipal();
        generatePayslip(token,career.id(),1);assertThat(paymentRepository.count()).isZero();generatePayslip(token,career.id(),2);assertThat(paymentRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(created.id())).singleElement().satisfies(p->{assertThat(p.getPaymentType()).isEqualTo(FinancialPaymentType.AUTO);assertThat(p.getOperationalWeek()).isEqualTo(2);});assertThat(contractRepository.findById(created.id()).orElseThrow().getRemainingPrincipal()).isLessThan(originalPrincipal);assertThat(ledgerRepository.findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(career.id(),Pageable.unpaged())).anyMatch(e->e.getEntryType()==LedgerEntryType.DEBT_PAYMENT&&e.getOperationalWeek()==2);assertThat(careerRepository.findById(career.id()).orElseThrow().getCurrentOperationalWeek()).isEqualTo(3);}

    private void generatePayslip(String token,UUID careerId,int week){restTestClient.post().uri(CAREERS+"/"+careerId+"/payslips?game=ATS").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",week)).exchange().expectStatus().isCreated();}
    private String financingPath(UUID careerId){return CAREERS+"/"+careerId+"/financing/contracts?game=ATS";}
    private CareerResponse createCareer(String token){Map<String,Object> request=new LinkedHashMap<>();request.put("game","ATS");request.put("driverName","Payroll Finance Driver");request.put("companyName","Road Logistics");request.put("initialBalance",new BigDecimal("5000.00"));request.put("baseCurrency","USD");request.put("displayCurrency","USD");request.put("exchangeRate",new BigDecimal("1.00000000"));request.put("exchangeRateAsOf","2026-08-28");request.put("stateCode","AZ");request.put("countryCode","US");request.put("baseCity","Phoenix, AZ");request.put("cityMarketVersion","test-v1");request.put("cityMarketLabel","Test market");request.put("cityCostFactor",new BigDecimal("1.0000"));request.put("citySalaryFactor",new BigDecimal("1.0000"));return Objects.requireNonNull(restTestClient.post().uri(CAREERS).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(request).exchange().expectStatus().isCreated().expectBody(CareerResponse.class).returnResult().getResponseBody());}
    private UserEntity saveUser(String email){Instant now=Instant.now().minus(1,ChronoUnit.MINUTES);return userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(),email,email.toLowerCase(),"encoded-password-not-used",email,UserStatus.ACTIVE,UserRole.USER,true,now,now,now,null));}
}
