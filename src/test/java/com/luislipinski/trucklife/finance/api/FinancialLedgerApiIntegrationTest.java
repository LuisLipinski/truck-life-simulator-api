package com.luislipinski.trucklife.finance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
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
class FinancialLedgerApiIntegrationTest {
    private static final String CAREERS_PATH="/api/v1/careers";
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired RestTestClient restTestClient;@Autowired UserRepository userRepository;@Autowired CareerRepository careerRepository;
    @Autowired LedgerEntryRepository ledgerRepository;@Autowired JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach void clean(){ledgerRepository.deleteAllInBatch();careerRepository.deleteAllInBatch();userRepository.deleteAllInBatch();}

    @Test void exposesImmutableOpeningAndIdempotentManualAdjustmentsWithOwnership(){
        UserEntity owner=saveUser("ledger-owner@example.com"),intruder=saveUser("ledger-intruder@example.com");String token=accessToken(owner),intruderToken=accessToken(intruder);
        CareerResponse career=createCareer(token,"Ledger Driver","5000.00");
        LedgerEntryResponse[] initial=ledger(token,career.id());assertThat(initial).singleElement().satisfies(e->{assertThat(e.type()).isEqualTo(LedgerEntryType.OPENING_BALANCE);assertThat(e.amount()).isEqualByComparingTo("5000.00");assertThat(e.balanceBefore()).isEqualByComparingTo("0.00");assertThat(e.balanceAfter()).isEqualByComparingTo("5000.00");});
        UUID operationId=UUID.randomUUID();Map<String,Object> adjustment=Map.of("operationId",operationId,"expectedOperationalWeek",1,"expectedBalance",new BigDecimal("5000.00"),"newBalance",new BigDecimal("4500.00"),"note","Synchronize with simulator");
        restTestClient.post().uri(financeActionPath(career.id(),"balance-adjustments")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(adjustment).exchange().expectStatus().isCreated().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL,"no-store").expectBody().jsonPath("$.type").isEqualTo("BALANCE_ADJUSTMENT").jsonPath("$.amount").isEqualTo(-500.0).jsonPath("$.balanceAfter").isEqualTo(4500.0);
        restTestClient.post().uri(financeActionPath(career.id(),"balance-adjustments")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(adjustment).exchange().expectStatus().isCreated();
        assertThat(ledgerRepository.countByCareerId(career.id())).isEqualTo(2);assertThat(careerRepository.findById(career.id()).orElseThrow().getBalance()).isEqualByComparingTo("4500.00");
        restTestClient.post().uri(financeActionPath(career.id(),"balance-adjustments")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("operationId",UUID.randomUUID(),"expectedOperationalWeek",1,"expectedBalance",new BigDecimal("5000.00"),"newBalance",new BigDecimal("4300.00"))).exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("LEDGER_BALANCE_CONFLICT");
        restTestClient.get().uri(financeActionPath(career.id(),"ledger")).headers(h->h.setBearerAuth(intruderToken)).exchange().expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");
        restTestClient.get().uri(financeActionPath(career.id(),"ledger")).exchange().expectStatus().isUnauthorized();
    }

    @Test void recordsFinanceAndPayslipMovementsInOneOrderedHistory(){
        UserEntity owner=saveUser("ledger-finance@example.com");String token=accessToken(owner);CareerResponse career=createCareer(token,"Finance Ledger","10000.00");
        restTestClient.get().uri(financePath(career.id())).headers(h->h.setBearerAuth(token)).exchange().expectStatus().isOk();
        restTestClient.post().uri(financeActionPath(career.id(),"emergency-reserve/deposits")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("operationId",UUID.randomUUID(),"expectedOperationalWeek",1,"amount",new BigDecimal("1000.00"))).exchange().expectStatus().isCreated();
        restTestClient.patch().uri(financeActionPath(career.id(),"emergency-reserve/auto-contribution")).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",1,"enabled",true,"amount",new BigDecimal("50.00"))).exchange().expectStatus().isOk();
        restTestClient.post().uri(CAREERS_PATH+"/"+career.id()+"/payslips?game=ATS").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",1)).exchange().expectStatus().isCreated();
        LedgerEntryResponse[] entries=ledger(token,career.id());assertThat(entries).extracting(LedgerEntryResponse::type).contains(LedgerEntryType.OPENING_BALANCE,LedgerEntryType.RESERVE_DEPOSIT,LedgerEntryType.RESERVE_INTEREST,LedgerEntryType.RESERVE_AUTO_CONTRIBUTION,LedgerEntryType.PAYSLIP_CREDIT);
        assertThat(entries).allSatisfy(e->assertThat(e.balanceAfter()).isEqualByComparingTo(e.balanceBefore().add(e.balanceDelta())));
    }

    @Test void supportsNegativeOpeningBalancesAndDocumentsLedgerEndpoints(){
        UserEntity owner=saveUser("ledger-negative@example.com");String token=accessToken(owner);CareerResponse career=createCareer(token,"Negative Ledger","-250.00");
        LedgerEntryResponse[] entries=ledger(token,career.id());assertThat(entries).singleElement().satisfies(e->{assertThat(e.type()).isEqualTo(LedgerEntryType.OPENING_BALANCE);assertThat(e.balanceAfter()).isEqualByComparingTo("-250.00");});
        restTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().jsonPath("$.paths['/api/v1/careers/{careerId}/finances/ledger'].get.responses['200']").exists().jsonPath("$.paths['/api/v1/careers/{careerId}/finances/balance-adjustments'].post.responses['201']").exists();
    }

    private LedgerEntryResponse[] ledger(String token,UUID careerId){return Objects.requireNonNull(restTestClient.get().uri(financeActionPath(careerId,"ledger")).headers(h->h.setBearerAuth(token)).exchange().expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL,"no-store").expectBody(LedgerEntryResponse[].class).returnResult().getResponseBody());}
    private String financePath(UUID careerId){return CAREERS_PATH+"/"+careerId+"/finances?game=ATS";}
    private String financeActionPath(UUID careerId,String action){return CAREERS_PATH+"/"+careerId+"/finances/"+action+"?game=ATS";}
    private CareerResponse createCareer(String token,String driver,String balance){Map<String,Object> request=new LinkedHashMap<>();request.put("game","ATS");request.put("driverName",driver);request.put("companyName","Road Logistics");request.put("initialBalance",new BigDecimal(balance));request.put("baseCurrency","USD");request.put("displayCurrency","USD");request.put("exchangeRate",new BigDecimal("1.00000000"));request.put("exchangeRateAsOf","2026-08-28");request.put("stateCode","AZ");request.put("baseCity","Phoenix, AZ");request.put("cityMarketVersion","test-v1");request.put("cityMarketLabel","Test market");request.put("cityCostFactor",new BigDecimal("1.0000"));request.put("citySalaryFactor",new BigDecimal("1.0000"));return Objects.requireNonNull(restTestClient.post().uri(CAREERS_PATH).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(request).exchange().expectStatus().isCreated().expectBody(CareerResponse.class).returnResult().getResponseBody());}
    private UserEntity saveUser(String email){Instant now=Instant.now().minus(1,ChronoUnit.MINUTES);return userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(),email,email.toLowerCase(),"encoded-password-not-used",email,UserStatus.ACTIVE,UserRole.USER,true,now,now,now,null));}
    private String accessToken(UserEntity user){return accessTokenIssuer.issue(user,UUID.randomUUID()).token();}
}
