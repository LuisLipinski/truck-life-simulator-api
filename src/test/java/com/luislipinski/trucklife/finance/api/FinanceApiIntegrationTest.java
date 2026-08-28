package com.luislipinski.trucklife.finance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.persistence.EmergencyReserveEventRepository;
import com.luislipinski.trucklife.finance.persistence.MonthlyExpenseApplicationRepository;
import com.luislipinski.trucklife.finance.persistence.MonthlyExpenseRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.payroll.api.PayslipResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient @ActiveProfiles("test") @Testcontainers
class FinanceApiIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired RestTestClient restTestClient;@Autowired UserRepository userRepository;@Autowired CareerRepository careerRepository;@Autowired MonthlyExpenseRepository expenseRepository;
    @Autowired MonthlyExpenseApplicationRepository applicationRepository;@Autowired EmergencyReserveEventRepository reserveEventRepository;@Autowired JwtAccessTokenIssuer accessTokenIssuer;
    @Autowired PasswordEncoder passwordEncoder;@Autowired JdbcTemplate jdbcTemplate;
    @BeforeEach void clean(){jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");}

    @Test void persistsExpensesAndMakesApplicationsAndReserveTransfersIdempotent(){
        UserEntity owner=user("finance-owner@example.com");String token=token(owner);CareerEntity career=career(owner,CareerGame.ATS,"5000.00");
        FinanceResponse initial=get(token,career.getId(),CareerGame.ATS);assertThat(initial.expenses()).hasSize(11);assertThat(initial.monthlyExpenseTotal()).isEqualByComparingTo("2310.36");assertThat(initial.emergencyReserve().balance()).isEqualByComparingTo("0.00");assertThat(initial.emergencyReserve().annualYieldRate()).isEqualByComparingTo("0.032500");
        FinanceResponse custom=Objects.requireNonNull(restTestClient.post().uri(path(career.getId(),"/monthly-expenses",CareerGame.ATS)).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",1,"name","Parking","amount",new BigDecimal("50.00"),"included",true)).exchange().expectStatus().isCreated().expectBody(FinanceResponse.class).returnResult().getResponseBody());
        assertThat(custom.expenses()).hasSize(12);assertThat(custom.monthlyExpenseTotal()).isEqualByComparingTo("2360.36");
        UUID applyId=UUID.randomUUID();FinanceResponse applied=post(token,career.getId(),"/monthly-expense-applications",Map.of("operationId",applyId,"expectedOperationalWeek",1));assertThat(applied.balance()).isEqualByComparingTo("2639.64");
        FinanceResponse retry=post(token,career.getId(),"/monthly-expense-applications",Map.of("operationId",applyId,"expectedOperationalWeek",1));assertThat(retry.balance()).isEqualByComparingTo("2639.64");assertThat(applicationRepository.count()).isEqualTo(1);
        UUID depositId=UUID.randomUUID();FinanceResponse deposit=post(token,career.getId(),"/emergency-reserve/deposits",Map.of("operationId",depositId,"expectedOperationalWeek",1,"amount",new BigDecimal("100.00")));assertThat(deposit.balance()).isEqualByComparingTo("2539.64");assertThat(deposit.emergencyReserve().balance()).isEqualByComparingTo("100.00");
        FinanceResponse depositRetry=post(token,career.getId(),"/emergency-reserve/deposits",Map.of("operationId",depositId,"expectedOperationalWeek",1,"amount",new BigDecimal("100.00")));assertThat(depositRetry.balance()).isEqualByComparingTo("2539.64");assertThat(reserveEventRepository.count()).isEqualTo(1);
        FinanceResponse withdrawn=post(token,career.getId(),"/emergency-reserve/withdrawals",Map.of("operationId",UUID.randomUUID(),"expectedOperationalWeek",1,"amount",new BigDecimal("20.00"),"reason","Emergency repair"));assertThat(withdrawn.balance()).isEqualByComparingTo("2559.64");assertThat(withdrawn.emergencyReserve().balance()).isEqualByComparingTo("80.00");
    }

    @Test void appliesWeeklyYieldAndAutomaticContributionAtomicallyWithTheAtsPayslip(){
        UserEntity owner=user("finance-payslip@example.com");String token=token(owner);CareerEntity career=career(owner,CareerGame.ATS,"5000.00");get(token,career.getId(),CareerGame.ATS);
        post(token,career.getId(),"/emergency-reserve/deposits",Map.of("operationId",UUID.randomUUID(),"expectedOperationalWeek",1,"amount",new BigDecimal("80.00")));
        FinanceResponse configured=Objects.requireNonNull(restTestClient.patch().uri(path(career.getId(),"/emergency-reserve/auto-contribution",CareerGame.ATS)).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",1,"enabled",true,"amount",new BigDecimal("25.00"))).exchange().expectStatus().isOk().expectBody(FinanceResponse.class).returnResult().getResponseBody());
        BigDecimal balanceBefore=configured.balance();
        PayslipResponse payslip=Objects.requireNonNull(restTestClient.post().uri("/api/v1/careers/"+career.getId()+"/payslips?game=ATS").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",1)).exchange().expectStatus().isCreated().expectBody(PayslipResponse.class).returnResult().getResponseBody());
        assertThat(payslip.reserveInterestAmount()).isEqualByComparingTo("0.05");assertThat(payslip.reserveContributionAmount()).isEqualByComparingTo("25.00");assertThat(payslip.balanceCreditAmount()).isEqualByComparingTo(payslip.depositAmount().subtract(new BigDecimal("25.00")));assertThat(payslip.contextSnapshot().get("emergencyReserveIncluded")).isEqualTo(true);
        FinanceResponse after=get(token,career.getId(),CareerGame.ATS);assertThat(after.emergencyReserve().balance()).isEqualByComparingTo("105.05");assertThat(after.balance()).isEqualByComparingTo(balanceBefore.add(payslip.balanceCreditAmount()));assertThat(after.currentOperationalWeek()).isEqualTo(2);assertThat(reserveEventRepository.count()).isEqualTo(3);
        restTestClient.post().uri("/api/v1/careers/"+career.getId()+"/payslips?game=ATS").headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek",1)).exchange().expectStatus().isEqualTo(409);
        assertThat(get(token,career.getId(),CareerGame.ATS).emergencyReserve().balance()).isEqualByComparingTo("105.05");assertThat(reserveEventRepository.count()).isEqualTo(3);
    }

    @Test void enforcesOwnershipStaleContextAndDocumentsFinanceApi(){
        UserEntity owner=user("finance-private@example.com"),intruder=user("finance-intruder@example.com");String ownerToken=token(owner),intruderToken=token(intruder);CareerEntity career=career(owner,CareerGame.ATS,"1000.00");
        restTestClient.get().uri(path(career.getId(),"",CareerGame.ATS)).headers(h->h.setBearerAuth(intruderToken)).exchange().expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");
        restTestClient.get().uri(path(career.getId(),"",CareerGame.ATS)).exchange().expectStatus().isUnauthorized();
        restTestClient.post().uri(path(career.getId(),"/emergency-reserve/deposits",CareerGame.ATS)).headers(h->h.setBearerAuth(ownerToken)).contentType(MediaType.APPLICATION_JSON).body(Map.of("operationId",UUID.randomUUID(),"expectedOperationalWeek",2,"amount",new BigDecimal("10"))).exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("FINANCE_WEEK_CONFLICT");
        restTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().jsonPath("$.paths['/api/v1/careers/{careerId}/finances'].get").exists().jsonPath("$.paths['/api/v1/careers/{careerId}/finances/monthly-expense-applications'].post").exists().jsonPath("$.paths['/api/v1/careers/{careerId}/finances/emergency-reserve/deposits'].post").exists().jsonPath("$.paths['/api/v1/careers/{careerId}/finances/emergency-reserve/auto-contribution'].patch").exists();
    }

    private FinanceResponse get(String token,UUID careerId,CareerGame game){return Objects.requireNonNull(restTestClient.get().uri(path(careerId,"",game)).headers(h->h.setBearerAuth(token)).exchange().expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL,"no-store").expectBody(FinanceResponse.class).returnResult().getResponseBody());}
    private FinanceResponse post(String token,UUID careerId,String suffix,Map<String,Object> body){return Objects.requireNonNull(restTestClient.post().uri(path(careerId,suffix,CareerGame.ATS)).headers(h->h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated().expectBody(FinanceResponse.class).returnResult().getResponseBody());}
    private String path(UUID careerId,String suffix,CareerGame game){return "/api/v1/careers/"+careerId+"/finances"+suffix+"?game="+game;}
    private String token(UserEntity user){return accessTokenIssuer.issue(user,UUID.randomUUID()).token();}
    private UserEntity user(String email){Instant now=Instant.parse("2026-08-27T12:00:00Z");return userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(),email,email.toLowerCase(),passwordEncoder.encode("unused valid password"),"Driver",UserStatus.ACTIVE,UserRole.USER,true,now,now,now,null));}
    private CareerEntity career(UserEntity owner,CareerGame game,String balance){Instant now=Instant.parse("2026-08-27T12:00:00Z");CareerEntity entity=new CareerEntity(UUID.randomUUID(),owner.getId(),game,"Driver","Company","",(short)1,new BigDecimal(balance),game==CareerGame.ATS?"USD":"EUR",game==CareerGame.ATS?"USD":"EUR",BigDecimal.ONE,LocalDate.of(2026,8,27),game==CareerGame.ATS?"AZ":null,game==CareerGame.ETS2?"DE":null,game==CareerGame.ATS?"Phoenix, AZ":"Berlin, Alemanha",null,null,"1","test",BigDecimal.ONE,BigDecimal.ONE,1,game==CareerGame.ETS2?1:null,now,now);return careerRepository.saveAndFlush(entity);}
}
