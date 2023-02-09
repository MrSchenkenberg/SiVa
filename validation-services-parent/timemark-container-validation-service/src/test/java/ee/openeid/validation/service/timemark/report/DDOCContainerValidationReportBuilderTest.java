package ee.openeid.validation.service.timemark.report;

import ee.openeid.siva.validation.document.ValidationDocument;
import ee.openeid.siva.validation.document.builder.DummyValidationDocumentBuilder;
import ee.openeid.siva.validation.document.report.Reports;
import ee.openeid.siva.validation.document.report.ValidationConclusion;
import ee.openeid.siva.validation.service.signature.policy.properties.ValidationPolicy;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.ContainerValidationResult;
import org.digidoc4j.ddoc.DigiDocException;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.impl.ddoc.DDocSignatureValidationResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;


public class DDOCContainerValidationReportBuilderTest {

    private static final String VALID_DDOC_WITH_2_SIGNATURES = "ddoc_valid_2_signatures.ddoc";

    @Test
    public void validDDOCReturnsSuccessfulResult() {
        ValidationDocument validationDocument = validationDocument();
        Container container = ContainerBuilder.aContainer()
                .fromStream(new ByteArrayInputStream(validationDocument.getBytes()))
                .build();

        ContainerValidationResult validationResult = container.validate();
        Reports reports = new DDOCContainerValidationReportBuilder(container, validationDocument, new ValidationPolicy(), validationResult, true).build();

        ValidationConclusion validationConclusion = reports.getSimpleReport().getValidationConclusion();
        assertSame(validationConclusion.getSignatures().size(), validationConclusion.getValidSignaturesCount());
    }

    @Test
    public void ifThereAreContainerErrorsThatAreNotPresentUnderSignaturesErrorsThenExceptionIsThrown() {

        ValidationDocument validationDocument = validationDocument();
        Container container = ContainerBuilder.aContainer()
                .fromStream(new ByteArrayInputStream(validationDocument.getBytes()))
                .build();
        ContainerValidationResult validationResult = new DDocSignatureValidationResult(Collections.singletonList(new DigiDocException(100, "Container level error", new RuntimeException())), "DIGIDOC-XML");
        validationResult.getContainerErrors().add(new DigiDoc4JException("Container level error"));

        Reports reports = new DDOCContainerValidationReportBuilder(container, validationDocument, new ValidationPolicy(), validationResult, true).build();
        Assertions.assertEquals(1, reports.getSimpleReport().getValidationConclusion().getSignatures().get(0).getErrors().size());
    }

    private ValidationDocument validationDocument() {
        return DummyValidationDocumentBuilder
                .aValidationDocument()
                .withDocument("test-files/" + VALID_DDOC_WITH_2_SIGNATURES)
                .withName(VALID_DDOC_WITH_2_SIGNATURES)
                .build();
    }
}
