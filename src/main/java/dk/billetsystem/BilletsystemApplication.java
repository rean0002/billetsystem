package dk.billetsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Programmets startpunkt. Når du trykker "Run" på denne klasse, starter Spring Boot:
 * 1. en indbygget webserver (som standard på http://localhost:8080)
 * 2. forbindelsen til databasen (oplysningerne står i application.properties)
 * 3. alle klasserne i denne pakke og dens undermapper (model, repository, service, controller)
 */
@SpringBootApplication
public class BilletsystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilletsystemApplication.class, args);
    }
}
