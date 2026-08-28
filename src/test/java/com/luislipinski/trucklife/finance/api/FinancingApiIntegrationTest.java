package com.luislipinski.trucklife.finance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.domain.FinancialContractEventType;
import com.luislipinski.trucklife.finance.domain.FinancialContractStatus;
import com.luislipinski.trucklife.finance.domain.FinancialInstallmentStatus;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient @ActiveProfiles("test") @Testcontainers
class FinancingApiIntegrationTest {
    private static final String CAREERS="/api/v1/careers";
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired RestTestClient restTestClient;@Autowired UserRepository userRepository;@Autowired CareerRepository careerRepository;
    @Autowired FinancialContractRepository contractRepository;@Autowired FinancialInstallmentRepository installmentRepository;@Autowired FinancialPaymentRepository paymentRepository;@Autowired FinancialContractEventRepository eventRepository;@Autowired LedgerEntryRepository ledgerRepository;@Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach void clean(){ledgerRepository.deleteAllInBatch();eventRepository.deleteAllInBatch();paymentRepository.deleteAllInBatch();installmentRepository.deleteAllInBatch();contractRepository.deleteAllInBatch();careerRepository.deleteAllInBatch();userRepository.deleteAllInBatch();}

    @Test void createsIdempotentAtsPersonalLoanFromServerPolicyAndSupportsExtraPrincipalAndPayoff(){UserEntity owner=saveUser("financing-owner@example.com");String token=token(owner);CareerResponse career=createCareer(token,"ATS","5000.00","USD","US","AZ","Phoenix, AZ");
        FinancingOfferResponse[] offers=Objects.requireNonNull(restTestClient.get().uri(path(career.id(),"/offers")+"&productType=PERSONAL_LOAN&requestedAmount=1000.00").headers(h->h.setBearerAuth(token)).exchange().expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL,"no-store").expectBody(FinancingOfferResponse[].class).returnResult().getResponseBody());assertThat(offers).hasSize(2);assertThat(offers[0].annualInterestRate()).isEqualByComparingTo("0.1186000000");
        UUID operation=UUID.randomUUID();Map<String,Object> request=contractRequest(operation,"PERSONAL_LOAN","1000.00",52,1,null,"5000.00");FinancialContractResponse created=createContract(token,career.id(),"ATS",request);assertThat(created.status()).isEqualTo(FinancialContractStatus.ACTIVE);assertThat(created.principal()).isEqualByComparingTo("1000.00");assertThat(created.installments()).hasSize(52);assertThat(created.installments().getFirst().dueOperationalWeek()).isEqualTo(2);assertThat(created.installments().getFirst().duePayrollMonth()).isNull();assertThat(careerRepository.findById(career.id()).orElseThrow().getBalance()).isEqualByComparingTo("6000.00");assertThat(ledgerRepository.findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(career.id())).anyMatch(e->e.getEntryType()==LedgerEntryType.LOAN_DISBURSEMENT);
        createContract(token,career.id(),"ATS",request);assertThat(contractRepository.count()).isEqualTo(1);
        FinancialContractResponse extra=pay(token,career.id(),created.id(),"ATS",Map.of("operationId",UUID.randomUUID(),"paymentType","EXTRA_PRINCIPAL","amount",new BigDecimal("100.00"),"expectedOperationalWeek",1,"expectedBalance",new BigDecimal("6000.00")));assertThat(extra.remainingPrincipal()).isEqualByComparingTo("900.00");assertThat(extra.currentScheduleVersion()).isEqualTo(2);assertThat(extra.events()).extracting(FinancialContractEventResponse::eventType).contains(FinancialContractEventType.RESCHEDULED);assertThat(extra.installments()).anyMatch(i->i.scheduleVersion()==1&&i.status()==FinancialInstallmentStatus.SUPERSEDED).anyMatch(i->i.scheduleVersion()==2&&i.status()==FinancialInstallmentStatus.SCHEDULED);
        FinancialContractResponse paid=pay(token,career.id(),created.id(),"ATS",Map.of("operationId",UUID.randomUUID(),"paymentType","PAYOFF","expectedOperationalWeek",1,"expectedBalance",new BigDecimal("5900.00")));assertThat(paid.status()).isEqualTo(FinancialContractStatus.PAID_OFF);assertThat(paid.remainingPrincipal()).isEqualByComparingTo("0.00");assertThat(careerRepository.findById(career.id()).orElseThrow().getBalance()).isEqualByComparingTo("5000.00");assertThat(paymentRepository.count()).isEqualTo(2);assertThat(ledgerRepository.findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(career.id())).filteredOn(e->e.getEntryType()==LedgerEntryType.DEBT_PAYMENT).hasSize(2);
    }

    @Test void vehicleFinancingDebitsOnlyServerCalculatedDownPaymentWithoutInventingAnAsset(){UserEntity owner=saveUser("vehicle-finance@example.com");String token=token(owner);CareerResponse career=createCareer(token,"ATS","30000.00","USD","US","CA","Los Angeles, CA");FinancialContractResponse contract=createContract(token,career.id(),"ATS",contractRequest(UUID.randomUUID(),"VEHICLE_FINANCING","100000.00",78,1,null,"30000.00"));assertThat(contract.downPayment()).isEqualByComparingTo("20000.00");assertThat(contract.principal()).isEqualByComparingTo("80000.00");assertThat(contract.installments().getFirst().dueOperationalWeek()).isEqualTo(3);assertThat(careerRepository.findById(career.id()).orElseThrow().getBalance()).isEqualByComparingTo("10000.00");assertThat(ledgerRepository.findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(career.id())).anyMatch(e->e.getEntryType()==LedgerEntryType.FINANCING_DOWN_PAYMENT);
    }

    @Test void ets2UsesOperationalMonthsAndCountryPolicyWhileUnresearchedCountriesAreBlocked(){UserEntity owner=saveUser("ets-finance@example.com");String token=token(owner);CareerResponse germany=createCareer(token,"ETS2","5000.00","EUR","DE",null,"Berlin");FinancialContractResponse contract=createContract(token,germany.id(),"ETS2",contractRequest(UUID.randomUUID(),"PERSONAL_LOAN","1000.00",12,1,1,"5000.00"));assertThat(contract.annualInterestRate()).isEqualByComparingTo("0.0812000000");assertThat(contract.originatedPayrollMonth()).isEqualTo(1);assertThat(contract.installments().getFirst().dueOperationalWeek()).isNull();assertThat(contract.installments().getFirst().duePayrollMonth()).isEqualTo(2);
        CareerResponse poland=createCareer(token,"ETS2","5000.00","PLN","PL",null,"Warsaw");restTestClient.get().uri(path(poland.id(),"/offers").replace("game=ATS","game=ETS2")+"&productType=PERSONAL_LOAN&requestedAmount=1000.00").headers(h->h.setBearerAuth(token)).exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("FINANCING_POLICY_UNAVAILABLE");}

    @Test void enforcesOwnershipAndDocumentsFinancingEndpoints(){UserEntity owner=saveUser("financing-docs@example.com"),intruder=saveUser("financing-intruder@example.com");String ownerToken=token(owner),intruderToken=token(intruder);CareerResponse career=createCareer(ownerToken,"ATS","5000.00","USD","US","TX","Dallas, TX");FinancialContractResponse contract=createContract(ownerToken,career.id(),"ATS",contractRequest(UUID.randomUUID(),"PERSONAL_LOAN","500.00",52,1,null,"5000.00"));restTestClient.get().uri(path(career.id(),"/contracts/"+contract.id())).headers(h->h.setBearerAuth(intruderToken)).exchange().expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");restTestClient.get().uri(path(career.id(),"/contracts")).exchange().expectStatus().isUnauthorized();restTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().jsonPath("$.paths['/api/v1/careers/{careerId}/financing/offers'].get.responses['200']").exists().jsonPath("$.paths['/api/v1/careers/{careerId}/financing/contracts'].post.responses['201']").exists().jsonPath("$.paths['/api/v1/careers/{careerId}/financing/contracts/{contractId}/payments'].post.responses['200']").exists();}

    private FinancialContractResponse createContract(String token,UUID careerId,String game,Map<String,Object> body){return Objects.requireNonNull(restTestClient.post().uri(path(careerId,"/contracts").replace("game=ATS","game="+game)).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL,"no-store").expectBody(FinancialContractResponse.class).returnResult().getResponseBody());}
    private FinancialContractResponse pay(String token,UUID careerId,UUID contractId,String game,Map<String,Object> body){return Objects.requireNonNull(restTestClient.post().uri(path(careerId,"/contracts/"+contractId+"/payments").replace("game=ATS","game="+game)).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isOk().expectBody(FinancialContractResponse.class).returnResult().getResponseBody());}
    private Map<String,Object> contractRequest(UUID operationId,String type,String amount,int term,int week,Integer month,String balance){Map<String,Object> value=new LinkedHashMap<>();value.put("operationId",operationId);value.put("productType",type);value.put("requestedAmount",new BigDecimal(amount));value.put("termPeriods",term);value.put("expectedOperationalWeek",week);if(month!=null)value.put("expectedPayrollMonth",month);value.put("expectedBalance",new BigDecimal(balance));return value;}
    private String path(UUID careerId,String suffix){return CAREERS+"/"+careerId+"/financing"+suffix+"?game=ATS";}
    private CareerResponse createCareer(String token,String game,String balance,String currency,String country,String state,String city){Map<String,Object> request=new LinkedHashMap<>();request.put("game",game);request.put("driverName","Finance Driver");request.put("companyName","Road Logistics");request.put("initialBalance",new BigDecimal(balance));request.put("baseCurrency",currency);request.put("displayCurrency",currency);request.put("exchangeRate",new BigDecimal("1.00000000"));request.put("exchangeRateAsOf","2026-08-28");if(state!=null)request.put("stateCode",state);if(country!=null)request.put("countryCode",country);request.put("baseCity",city);request.put("cityMarketVersion","test-v1");request.put("cityMarketLabel","Test market");request.put("cityCostFactor",new BigDecimal("1.0000"));request.put("citySalaryFactor",new BigDecimal("1.0000"));return Objects.requireNonNull(restTestClient.post().uri(CAREERS).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(request).exchange().expectStatus().isCreated().expectBody(CareerResponse.class).returnResult().getResponseBody());}
    private UserEntity saveUser(String email){Instant now=Instant.now().minus(1,ChronoUnit.MINUTES);return userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(),email,email.toLowerCase(),"encoded-password-not-used",email,UserStatus.ACTIVE,UserRole.USER,true,now,now,now,null));}
    private String token(UserEntity user){return tokenIssuer.issue(user,UUID.randomUUID()).token();}
}
