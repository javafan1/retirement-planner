package com.daviddunn.retirementplanner.domain.projection;

import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CalculationModeOldReaderTest {
    @TempDir Path directory;
    @Test void predecessorAcceptsLegacyInputsAndRejectsExplicitModes() throws Exception {
        var source=directory.resolve("RetirementPlan.java");
        try(var input=getClass().getResourceAsStream("/phase23e/legacy/RetirementPlan.java.txt")) { assertNotNull(input);Files.write(source,input.readAllBytes()); }
        var classes=directory.resolve("classes");Files.createDirectories(classes);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","25","-classpath",System.getProperty("java.class.path"),"-d",classes.toString(),source.toString()));
        String name="com.daviddunn.retirementplanner.domain.model.RetirementPlan";
        try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                synchronized(getClassLoadingLock(requested)) {
                    if(!requested.equals(name))return super.loadClass(requested,resolve);
                    var type=findLoadedClass(requested);if(type==null)type=findClass(requested);if(resolve)resolveClass(type);return type;
                }
            }
        }) {
            var type=loader.loadClass(name);var mapper=new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            var plan=NonqualifiedAnnuityFinancialTest.fixture(true);
            assertNotNull(mapper.readValue(mapper.writeValueAsBytes(plan),type));
            for(var mode:com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.values()) {
                plan.setFinancialCalculationMode(mode);var bytes=mapper.writeValueAsBytes(plan);
                assertThrows(Exception.class,()->mapper.readValue(bytes,type));
            }
        }
    }
}
