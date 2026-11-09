package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the exact accepted predecessor's closed reader, not a simulated enum test. */
class NonqualifiedAnnuityOldReaderTest {
    @TempDir Path directory;
    @Test void acceptedPredecessorRejectsNewInputsInsteadOfLoadingThemAsOrdinaryIncome() throws Exception {
        var files=new ArrayList<String>();
        for(String name:List.of("ConditionalBenefitDefinition","ConditionalBenefitDefinitionDeserializer")) {
            var destination=directory.resolve(name+".java");
            try(var input=getClass().getResourceAsStream("/phase23d/legacy/"+name+".java.txt")) {
                assertNotNull(input);Files.write(destination,input.readAllBytes());files.add(destination.toString());
            }
        }
        var classes=directory.resolve("classes");Files.createDirectories(classes);
        var args=new ArrayList<>(List.of("--release","25","-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));args.addAll(files);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        String prefix="com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition";
        try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    if(!name.startsWith(prefix)) return super.loadClass(name,resolve);
                    var type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
                }
            }
        }) {
            var type=loader.loadClass(prefix);
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            assertNotNull(mapper.readValue(BenefitFixtures.JSON.writeValueAsString(BenefitFixtures.monthly()),type));
            var input=(com.fasterxml.jackson.databind.node.ObjectNode)BenefitFixtures.JSON.valueToTree(NonqualifiedAnnuityFinancialTest.fixture(true).getConditionalBenefits().getFirst());
            assertThrows(Exception.class,()->mapper.treeToValue(input,type));
            input.remove("annuityTerms");assertThrows(Exception.class,()->mapper.treeToValue(input,type));
            input.put("category","CONDITIONAL_INCOME");assertThrows(Exception.class,()->mapper.treeToValue(input,type));
        }
    }
}
