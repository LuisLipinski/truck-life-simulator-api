package com.luislipinski.trucklife.finance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.domain.FinancialContractEventType;
import com.luislipinski.trucklife.finance.domain.FinancialContractStatus;
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
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
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
    @Autowired RestTestClient restTestClient;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired FinancialContractRepository contractRepository;
    @Autowired FinancialInstallmentRepository installmentRepository;
    @Autowired FinancialPaymentRepository paymentRepository;
    @Autowired FinancialContractEventRepository eventRepository;
    @Autowired LedgerEntryRepository ledgerRepository;
    @Autowired PayslipLineRepository payslipLineRepository;
    @Autowired PayslipRepository payslipRepository;
    @Autowired PayrollPeriodRepository payrollPeriodRepository;
    @Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach void clean(){
        ledgerRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        installmentRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
        payslipLineRepository.deleteAllInBatch();
        payslipRepository.deleteAllInBatch();
        payrollPeriodRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test void atsPayslipAutomaticallyPaysDueWeeklyInstallmentInSameOperationalClose(){
        UserEntity owner=saveUser("financing-payroll@example.com");
        String token=tokenIssuer.issue(owner,UUID.randomUUID()).token();
        CareerResponse career=createAtsCareer(token);
        Map<String,Object> contract=new LinkedHashMap<>();
        contract.put("operationId",UUID.randomUUID());
        contract.put("productType","PERSONAL_LOAN");
        contract.put("requestedAmount",new BigDecimal("500.00"));
        contract.put("termPeriods",52);
        contract.put("expectedOperationalWeek",1);
        contract.put("expectedBalance",new BigDecimal("5000.00"));
        FinancialContractResponse created=Objects.requireNonNull(restTestClient.post().uri(financingPath(career.id(),"ATS")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(contract).exchange().expectStatus().isCreated().expectBody(FinancialContractResponse.class).returnResult().getResponseBody());
        BigDecimal originalPrincipal=created.remainingPrincipal();

        generateAtsPayslip(token,career.id(),1);
        assertThat(paymentRepository.count()).isZero();
        generateAtsPayslip(token,career.id(),2);

        assertThat(paymentRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(created.id())).singleElement().satisfies(p->{
            assertThat(p.getPaymentType()).isEqualTo(FinancialPaymentType.AUTO);
            assertThat(p.getOperationalWeek()).isEqualTo(2);
            assertThat(p.getPayrollMonth()).isNull();
        });
        assertThat(contractRepository.findById(created.id()).orElseThrow().getRemainingPrincipal()).isLessThan(originalPrincipal);
        assertThat(ledgerRepository.findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(career.id(),Pageable.unpaged())).anyMatch(e->e.getEntryType()==LedgerEntryType.DEBT_PAYMENT&&e.getOperationalWeek()==2);
        assertThat(careerRepository.findById(career.id()).orElseThrow().getCurrentOperationalWeek()).isEqualTo(3);
    }

    @Test void ets2MonthlyPayslipAutomaticallyPaysInstallmentOnlyWhenItsOperationalMonthIsDue(){
        UserEntity owner=saveUser("financing-payroll-ets2@example.com");
        String token=tokenIssuer.issue(owner,UUID.randomUUID()).token();
        CareerResponse career=createEts2Career(token);
        Map<String,Object> contract=new LinkedHashMap<>();
        contract.put("operationId",UUID.randomUUID());
        contract.put("productType","PERSONAL_LOAN");
        contract.put("requestedAmount",new BigDecimal("500.00"));
        contract.put("termPeriods",12);
        contract.put("expectedOperationalWeek",1);
        contract.put("expectedPayrollMonth",1);
        contract.put("expectedBalance",new BigDecimal("5000.00"));
        FinancialContractResponse created=Objects.requireNonNull(restTestClient.post().uri(financingPath(career.id(),"ETS2")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(contract).exchange().expectStatus().isCreated().expectBody(FinancialContractResponse.class).returnResult().getResponseBody());
        BigDecimal originalPrincipal=created.remainingPrincipal();
        assertThat(created.installments().getFirst().duePayrollMonth()).isEqualTo(2);

        closeEts2Week(token,career.id(),1);
        closeEts2Week(token,career.id(),2);
        closeEts2Week(token,career.id(),3);
        closeEts2Week(token,career.id(),4);
        generateEts2Payslip(token,career.id(),1);
        assertThat(paymentRepository.count()).isZero();

        closeEts2Week(token,career.id(),5);
        closeEts2Week(token,career.id(),6);
        closeEts2Week(token,career.id(),7);
        closeEts2Week(token,career.id(),8);
        generateEts2Payslip(token,career.id(),2);

        assertThat(paymentRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(created.id())).singleElement().satisfies(p->{
            assertThat(p.getPaymentType()).isEqualTo(FinancialPaymentType.AUTO);
            assertThat(p.getOperationalWeek()).isEqualTo(8);
            assertThat(p.getPayrollMonth()).isEqualTo(2);
        });
        assertThat(contractRepository.findById(created.id()).orElseThrow().getRemainingPrincipal()).isLessThan(originalPrincipal);
        assertThat(ledgerRepository.findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(career.id(),Pageable.unpaged())).anyMatch(e->e.getEntryType()==LedgerEntryType.DEBT_PAYMENT&&Integer.valueOf(2).equals(e.getPayrollMonth()));
        assertThat(careerRepository.findById(career.id()).orElseThrow().getCurrentPayrollMonth()).isEqualTo(3);
    }

    @Test void atsContractDefaultsWhenThreeInstallmentsRemainOverdueAfterAutomaticPayments(){
        UserEntity owner=saveUser("financing-default@example.com");
        String token=tokenIssuer.issue(owner,UUID.randomUUID()).token();
        CareerResponse career=createAtsCareer(token);
        Map<String,Object> contract=new LinkedHashMap<>();
        contract.put("operationId",UUID.randomUUID());
        contract.put("productType","PERSONAL_LOAN");
        contract.put("requestedAmount",new BigDecimal("100000.00"));
        contract.put("termPeriods",52);
        contract.put("expectedOperationalWeek",1);
        contract.put("expectedBalance",new BigDecimal("5000.00"));
        FinancialContractResponse created=Objects.requireNonNull(restTestClient.post().uri(financingPath(career.id(),"ATS")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(contract).exchange().expectStatus().isCreated().expectBody(FinancialContractResponse.class).returnResult().getResponseBody());
        assertThat(careerRepository.findById(career.id()).orElseThrow().getBalance()).isEqualByComparingTo("105000.00");

        adjustBalance(token,career.id(),new BigDecimal("105000.00"),BigDecimal.ZERO,1);
        generateAtsPayslip(token,career.id(),1);
        generateAtsPayslip(token,career.id(),2);

        assertThat(contractRepository.findById(created.id()).orElseThrow().getStatus()).isEqualTo(FinancialContractStatus.DELINQUENT);
        assertThat(eventRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(created.id())).extracting(e->e.getEventType()).contains(FinancialContractEventType.DELINQUENT);

        generateAtsPayslip(token,career.id(),3);
        generateAtsPayslip(token,career.id(),4);
        assertThat(contractRepository.findById(created.id()).orElseThrow().getStatus()).isEqualTo(FinancialContractStatus.DELINQUENT);
        generateAtsPayslip(token,career.id(),5);

        assertThat(contractRepository.findById(created.id()).orElseThrow().getStatus()).isEqualTo(FinancialContractStatus.DEFAULTED);
        assertThat(eventRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(created.id())).extracting(e->e.getEventType()).contains(FinancialContractEventType.DEFAULTED);
        assertThat(paymentRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(created.id())).hasSize(4).allSatisfy(p->assertThat(p.getPaymentType()).isEqualTo(FinancialPaymentType.AUTO));
    }

    private void generateAtsPayslip(String token,UUID careerId,int week){restTestClient.post().uri(CAREERS+"/"+careerId+"/payslips?game=ATS").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",week)).exchange().expectStatus().isCreated();}
    private void generateEts2Payslip(String token,UUID careerId,int month){restTestClient.post().uri(CAREERS+"/"+careerId+"/payslips?game=ETS2").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedPayrollMonth",month)).exchange().expectStatus().isCreated();}
    private void closeEts2Week(String token,UUID careerId,int week){restTestClient.post().uri(CAREERS+"/"+careerId+"/payroll-periods/close?game=ETS2").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",week)).exchange().expectStatus().isCreated();}
    private void adjustBalance(String token,UUID careerId,BigDecimal expectedBalance,BigDecimal newBalance,int week){Map<String,Object> body=new LinkedHashMap<>();body.put("operationId",UUID.randomUUID());body.put("expectedOperationalWeek",week);body.put("expectedBalance",expectedBalance);body.put("newBalance",newBalance);body.put("note","Financing delinquency integration fixture");restTestClient.post().uri(CAREERS+"/"+careerId+"/finances/balance-adjustments?game=ATS").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated();}
    private String financingPath(UUID careerId,String game){return CAREERS+"/"+careerId+"/financing/contracts?game="+game;}

    private CareerResponse createAtsCareer(String token){
        Map<String,Object> request=baseCareerRequest("ATS","Payroll Finance Driver","USD","US","Phoenix, AZ");
        request.put("stateCode","AZ");
        return createCareer(token,request);
    }

    private CareerResponse createEts2Career(String token){
        return createCareer(token,baseCareerRequest("ETS2","Payroll Finance ETS2 Driver","EUR","DE","Berlin"));
    }

    private Map<String,Object> baseCareerRequest(String game,String driverName,String currency,String country,String city){
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("game",game);
        request.put("driverName",driverName);
        request.put("companyName","Road Logistics");
        request.put("initialBalance",new BigDecimal("5000.00"));
        request.put("baseCurrency",currency);
        request.put("displayCurrency",currency);
        request.put("exchangeRate",new BigDecimal("1.00000000"));
        request.put("exchangeRateAsOf","2026-08-28");
        request.put("countryCode",country);
        request.put("baseCity",city);
        request.put("cityMarketVersion","test-v1");
        request.put("cityMarketLabel","Test market");
        request.put("cityCostFactor",new BigDecimal("1.0000"));
        request.put("citySalaryFactor",new BigDecimal("1.0000"));
        return request;
    }

    private CareerResponse createCareer(String token,Map<String,Object> request){return Objects.requireNonNull(restTestClient.post().uri(CAREERS).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(request).exchange().expectStatus().isCreated().expectBody(CareerResponse.class).returnResult().getResponseBody());}
    private UserEntity saveUser(String email){Instant now=Instant.now().minus(1,ChronoUnit.MINUTES);return userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(),email,email.toLowerCase(),"encoded-password-not-used",email,UserStatus.ACTIVE,UserRole.USER,true,now,now,now,null));}
}
