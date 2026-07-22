package com.saga.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BeApplication {

	public static void main(String[] args) {
		SpringApplication.run(BeApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner testMongoInsertion(com.saga.be.repository.SystemAuditLogRepository repo) {
		return args -> {
			System.out.println("====== DANG TEST LUU LOG VAO MONGO ATLAS ======");
			com.saga.be.entity.SystemAuditLog log = new com.saga.be.entity.SystemAuditLog();
			log.setActorId("system_test");
			log.setAction("TEST_STARTUP");
			log.setTargetEntity("Application");
			repo.save(log);
			System.out.println("====== DA LUU THANH CONG. BAN HAY CHECK LAI TRANG WEB ATLAS NHE! ======");
		};
	}

}
