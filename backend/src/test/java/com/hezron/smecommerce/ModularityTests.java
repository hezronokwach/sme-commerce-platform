package com.hezron.smecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Guards the module boundaries. Fails the build when a module uses another module's internal
 * types, depends on a module not listed in its {@code allowedDependencies}, or forms a cycle.
 * Needs no Spring context or database, so it runs in milliseconds.
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(SmeCommerceApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void writesDocumentationSnippets() {
        // PlantUML/C4 diagrams and module canvases, committed under docs/modules
        new Documenter(modules, Documenter.Options.defaults().withOutputFolder("../docs/modules"))
                .writeDocumentation();
    }

}
