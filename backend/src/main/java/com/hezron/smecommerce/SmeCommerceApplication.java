package com.hezron.smecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

// sharedModules: always bootstrapped alongside the module under test in @ApplicationModuleTest
@Modulithic(systemName = "SME Commerce", sharedModules = "shared")
@SpringBootApplication
public class SmeCommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmeCommerceApplication.class, args);
    }

}
