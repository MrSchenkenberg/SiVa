/*
 * Copyright 2020 Riigi Infosüsteemide Amet
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package ee.openeid.validation.service.timemark;

import ee.openeid.siva.validation.configuration.ReportConfigurationProperties;
import ee.openeid.siva.validation.document.ValidationDocument;
import ee.openeid.siva.validation.document.report.*;
import ee.openeid.siva.validation.exception.MalformedDocumentException;
import ee.openeid.siva.validation.service.signature.policy.ConstraintLoadingSignaturePolicyService;
import ee.openeid.siva.validation.service.signature.policy.InvalidPolicyException;
import ee.openeid.siva.validation.service.signature.policy.PredefinedValidationPolicySource;
import ee.openeid.siva.validation.util.CertUtil;
import ee.openeid.tsl.TSLLoader;
import ee.openeid.tsl.TSLValidationJobFactory;
import ee.openeid.tsl.configuration.TSLLoaderConfiguration;
import ee.openeid.validation.service.timemark.configuration.TimemarkContainerValidationServiceConfiguration;
import ee.openeid.validation.service.timemark.signature.policy.BDOCConfigurationService;
import ee.openeid.validation.service.timemark.signature.policy.BDOCSignaturePolicyService;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.service.http.proxy.ProxyConfig;
import eu.europa.esig.dss.spi.tsl.ConditionForQualifiers;
import eu.europa.esig.dss.spi.tsl.TrustProperties;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.digidoc4j.TSLCertificateSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static ee.openeid.validation.service.timemark.BDOCTestUtils.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
        TSLLoaderConfiguration.class,
        TSLLoader.class,
        TSLValidationJobFactory.class,
        TimemarkContainerValidationServiceConfiguration.class,
        TimemarkContainerValidationService.class,
        BDOCSignaturePolicyService.class,
        ConstraintLoadingSignaturePolicyService.class,
        BDOCConfigurationService.class,
        ReportConfigurationProperties.class,
        ProxyConfig.class
})
@ActiveProfiles("test")
public class TimemarkContainerValidationServiceIntegrationTest {

    private static final String QC_WITH_QSCD = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCWithQSCD";
    private static final String QC_STATEMENT = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCStatement";
    private static final String QC_FOR_ESIG = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCForESig";
    private static final String TEST_OF_KLASS3_SK_2010 = "TEST of KLASS3-SK 2010";
    private static String POL_V3 = "POLv3";
    private static String POL_V4 = "POLv4";

    private static String DOCUMENT_MALFORMED_MESSAGE = "Document malformed or not matching documentType";

    @Autowired
    private TimemarkContainerValidationService timemarkContainerValidationService;
    @Autowired
    private BDOCConfigurationService configurationService;
    @Autowired
    private ReportConfigurationProperties reportConfigurationProperties;


    @Test
    public void vShouldHaveSignatureWarnings() {
        SimpleReport validationResult = timemarkContainerValidationService.validateDocument(buildValidationDocument(BDOC_TEST_FILE_UNSIGNED)).getSimpleReport();
        List<Warning> signatureValidationData = validationResult.getValidationConclusion().getSignatures().get(0).getWarnings();
        assertThat(signatureValidationData, hasSize(1));
        assertThat(signatureValidationData, containsInAnyOrder(
                hasProperty("content", is("The signature/seal is not a valid AdES digital signature!"))
        ));
    }

    @Test
    public void vShouldNotHaveValidationWarnings() {
        SimpleReport validationResult = timemarkContainerValidationService.validateDocument(buildValidationDocument(BDOC_TEST_FILE_ALL_SIGNED)).getSimpleReport();
        List<ValidationWarning> validationWarnings = validationResult.getValidationConclusion().getValidationWarnings();
        assertThat(validationWarnings, hasSize(0));
    }

    @Test
    public void verifyCorrectPolicyIsLoadedToD4JConfiguration() {
        System.out.println(configurationService.loadPolicyConfiguration(null).getConfiguration().getValidationPolicy());

        System.out.println(configurationService.loadPolicyConfiguration(POL_V4).getConfiguration().getValidationPolicy());
        System.out.println(configurationService.loadPolicyConfiguration(POL_V3).getConfiguration().getValidationPolicy());
        assertTrue(configurationService.loadPolicyConfiguration(null).getConfiguration().getValidationPolicy().contains("siva-bdoc-POLv4-constraint"));
        assertTrue(configurationService.loadPolicyConfiguration(POL_V4).getConfiguration().getValidationPolicy().contains("siva-bdoc-POLv4-constraint"));
        assertTrue(configurationService.loadPolicyConfiguration(POL_V3).getConfiguration().getValidationPolicy().contains("siva-bdoc-POLv3-constraint"));
    }

    @Test
    public void validatingABDOCWithMalformedBytesResultsInMalformedDocumentException() {
        ValidationDocument validationDocument = new ValidationDocument();
        validationDocument.setBytes("Hello".getBytes());
        Assertions.assertThrows(MalformedDocumentException.class, () -> {
            timemarkContainerValidationService.validateDocument(validationDocument);
        }, DOCUMENT_MALFORMED_MESSAGE);
    }

    @Test
    public void bdocValidationResultShouldIncludeValidationReportPOJO() {
        SimpleReport validationResult2Signatures = timemarkContainerValidationService.validateDocument(bdocValid2Signatures()).getSimpleReport();
        assertNotNull(validationResult2Signatures);
    }

    @Test
    public void vShouldIncludeRequiredFields() {
        SimpleReport validationResult2Signatures = timemarkContainerValidationService.validateDocument(bdocValid2Signatures()).getSimpleReport();
        ValidationConclusion validationConclusion = validationResult2Signatures.getValidationConclusion();
        assertNotNull(validationConclusion.getPolicy());
        assertNotNull(validationConclusion.getValidationTime());
        assertEquals(VALID_BDOC_TM_2_SIGNATURES, validationConclusion.getValidatedDocument().getFilename());
        assertTrue(validationConclusion.getSignatures().size() == 2);
        assertTrue(validationConclusion.getValidSignaturesCount() == 2);
        assertTrue(validationConclusion.getSignaturesCount() == 2);
    }

    @Test
    public void signatureScopeShouldBeCorrectWhenDatafilesContainSpacesOrParenthesis() {
        SimpleReport report = timemarkContainerValidationService.validateDocument(buildValidationDocument(VALID_ID_CARD_MOB_ID)).getSimpleReport();
        report.getValidationConclusion().getSignatures().forEach(sig -> assertContainsScope(sig, "Proov (2).txt"));
        SimpleReport report2 = timemarkContainerValidationService.validateDocument(buildValidationDocument(VALID_BALTIC_EST_LT)).getSimpleReport();
        report2.getValidationConclusion().getSignatures().forEach(sig -> assertContainsScope(sig, "Baltic MoU digital signing_04112015.docx"));
    }

    private void assertContainsScope(SignatureValidationData signature, String filename) {
        assertTrue(signature.getSignatureScopes()
                .stream()
                .map(SignatureScope::getName)
                .filter(name -> StringUtils.equals(filename, name))
                .count() > 0);
    }

    @Test
    public void vShouldHaveCorrectSignatureValidationDataForSignature1() {

        SimpleReport validationResult2Signatures = timemarkContainerValidationService.validateDocument(bdocValid2Signatures()).getSimpleReport();
        SignatureValidationData sig1 = validationResult2Signatures.getValidationConclusion().getSignatures()
                .stream()
                .filter(sig -> sig.getId().equals("id-7b7180a5265f919bc0ac65bd02e4b46a"))
                .findFirst()
                .get();

        assertEquals("XAdES_BASELINE_LT_TM", sig1.getSignatureFormat());
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256", sig1.getSignatureMethod());
        assertEquals("QESIG", sig1.getSignatureLevel());
        assertEquals("JÕEORG,JAAK-KRISTJAN,38001085718", sig1.getSignedBy());
        assertEquals("JÕEORG,JAAK-KRISTJAN,38001085718", sig1.getSubjectDistinguishedName().getCommonName());
        assertEquals("PNOEE-38001085718", sig1.getSubjectDistinguishedName().getSerialNumber());
        assertEquals(SignatureValidationData.Indication.TOTAL_PASSED.toString(), sig1.getIndication());
        assertTrue(StringUtils.isEmpty(sig1.getSubIndication()));
        assertTrue(sig1.getErrors().size() == 0);
        assertTrue(sig1.getWarnings().size() == 0);
        assertTrue(sig1.getSignatureScopes().size() == 1);
        SignatureScope scope = sig1.getSignatureScopes().get(0);
        assertEquals("test.txt", scope.getName());
        assertEquals("Digest of the document content", scope.getContent());
        assertEquals("FullSignatureScope", scope.getScope());
        assertEquals("2020-05-21T14:07:04Z", sig1.getClaimedSigningTime());
        assertEquals("2020-05-21T14:07:01Z", sig1.getInfo().getBestSignatureTime());
        assertEquals("MDEwDQYJYIZIAWUDBAIBBQAEIGKrO2Grf+WLkmOnj9QQbCXAa2A3881D9PUIOk0M7Nm6", sig1.getInfo().getTimeAssertionMessageImprint());
        assertTrue(sig1.getInfo().getSignerRole().isEmpty());
        assertNull(sig1.getInfo().getSignatureProductionPlace());
    }

    @Test
    public void vShouldHaveCorrectSignatureValidationDataForSignature2() {
        SimpleReport validationResult2Signatures = timemarkContainerValidationService.validateDocument(bdocValid2Signatures()).getSimpleReport();
        SignatureValidationData sig2 = validationResult2Signatures.getValidationConclusion().getSignatures()
                .stream()
                .filter(sig -> sig.getId().equals("id-c71904f656e45af0c9b0ce644fc9287d"))
                .findFirst()
                .get();

        assertEquals("XAdES_BASELINE_LT_TM", sig2.getSignatureFormat());
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256", sig2.getSignatureMethod());
        assertEquals("QESIG", sig2.getSignatureLevel());
        assertEquals("MÄNNIK,MARI-LIIS,47101010033", sig2.getSignedBy());
        assertEquals("MÄNNIK,MARI-LIIS,47101010033", sig2.getSubjectDistinguishedName().getCommonName());
        assertEquals("47101010033", sig2.getSubjectDistinguishedName().getSerialNumber());
        assertEquals(SignatureValidationData.Indication.TOTAL_PASSED.toString(), sig2.getIndication());
        assertTrue(StringUtils.isEmpty(sig2.getSubIndication()));
        assertTrue(sig2.getErrors().size() == 0);
        assertTrue(sig2.getWarnings().size() == 0);
        assertTrue(sig2.getSignatureScopes().size() == 1);
        SignatureScope scope = sig2.getSignatureScopes().get(0);
        assertEquals("test.txt", scope.getName());
        assertEquals("Digest of the document content", scope.getContent());
        assertEquals("FullSignatureScope", scope.getScope());
        assertEquals("2020-05-28T10:59:12Z", sig2.getClaimedSigningTime());
        assertEquals("2020-05-28T10:59:14Z", sig2.getInfo().getBestSignatureTime());
        assertEquals("MDEwDQYJYIZIAWUDBAIBBQAEIDDnPj4HDgSwi+tj/s30GshbBf1L8Nqnt2GMK+6VnEdt", sig2.getInfo().getTimeAssertionMessageImprint());
        assertEquals(1, sig2.getInfo().getSignerRole().size());
        assertEquals("Signing as king of signers", sig2.getInfo().getSignerRole().get(0).getClaimedRole());
        assertEquals("Tallinn", sig2.getInfo().getSignatureProductionPlace().getCity());
        assertEquals("Harju", sig2.getInfo().getSignatureProductionPlace().getStateOrProvince());
        assertEquals("Elbonia", sig2.getInfo().getSignatureProductionPlace().getCountryName());
        assertEquals("32323", sig2.getInfo().getSignatureProductionPlace().getPostalCode());
    }

    @Test
    public void reportForBdocValidationShouldIncludeCorrectAsiceSignatureForm() {
        SimpleReport report = timemarkContainerValidationService.validateDocument(bdocValid2Signatures()).getSimpleReport();
        assertEquals("ASiC-E", report.getValidationConclusion().getSignatureForm());
    }

    @Test
    public void bestSignatureTimeInQualifiedBdocReportShouldNotBeBlank() {
        SimpleReport report = timemarkContainerValidationService.validateDocument(bdocValidIdCardAndMobIdSignatures()).getSimpleReport();
        ValidationConclusion validationConclusion = report.getValidationConclusion();
        String bestSignatureTime1 = validationConclusion.getSignatures().get(0).getInfo().getBestSignatureTime();
        String bestSignatureTime2 = validationConclusion.getSignatures().get(1).getInfo().getBestSignatureTime();
        assertTrue(StringUtils.isNotBlank(bestSignatureTime1));
        assertTrue(StringUtils.isNotBlank(bestSignatureTime2));
    }

    @Test
    public void bdocWithCRLRevocationDataOnlyShouldFail() throws Exception {
        SimpleReport report = timemarkContainerValidationService.validateDocument(bdocCRLRevocationOnly()).getSimpleReport();
        assertTrue(report.getValidationConclusion().getValidSignaturesCount() == 0);
    }

    @Test
    public void validationReportShouldContainDefaultPolicyWhenPolicyIsNotExplicitlyGiven() {
        Policy policy = validateWithPolicy("").getValidationConclusion().getPolicy();
        assertEquals(PredefinedValidationPolicySource.QES_POLICY.getName(), policy.getPolicyName());
        assertEquals(PredefinedValidationPolicySource.QES_POLICY.getDescription(), policy.getPolicyDescription());
        assertEquals(PredefinedValidationPolicySource.QES_POLICY.getUrl(), policy.getPolicyUrl());
    }

    @Test
    public void validationReportShouldContainAdesPolicyWhenAdesPolicyIsGivenToValidator() {
        Policy policy = validateWithPolicy(POL_V3).getValidationConclusion().getPolicy();
        assertEquals(PredefinedValidationPolicySource.ADES_POLICY.getName(), policy.getPolicyName());
        assertEquals(PredefinedValidationPolicySource.ADES_POLICY.getDescription(), policy.getPolicyDescription());
        assertEquals(PredefinedValidationPolicySource.ADES_POLICY.getUrl(), policy.getPolicyUrl());
    }

    @Test
    public void validationReportShouldContainQESPolicyWhenQESPolicyIsGivenToValidator() {
        Policy policy = validateWithPolicy(POL_V4).getValidationConclusion().getPolicy();
        assertEquals(PredefinedValidationPolicySource.QES_POLICY.getName(), policy.getPolicyName());
        assertEquals(PredefinedValidationPolicySource.QES_POLICY.getDescription(), policy.getPolicyDescription());
        assertEquals(PredefinedValidationPolicySource.QES_POLICY.getUrl(), policy.getPolicyUrl());
    }

    @Test
    public void whenNonExistingPolicyIsGivenThenValidatorShouldThrowException() {
        Assertions.assertThrows(InvalidPolicyException.class, () -> {
            validateWithPolicy("non-existing-policy").getValidationConclusion().getPolicy();
        });
    }

    @Test
    @Disabled("fails because of DSS bug: https://esig-dss.atlassian.net/browse/DSS-915")
    public void WhenAllQualifiersAreSetInServiceInfoThenSignatureLevelShouldBeQESAndValidWithPOLv4() throws Exception {
        testWithAllQualifiersSet(POL_V4);
    }

    @Test
    @Disabled("fails because of DSS bug: https://esig-dss.atlassian.net/browse/DSS-915")
    public void WhenAllQualifiersAreSetInServiceInfoThenSignatureLevelShouldBeQESAndValidWithPOLv3() throws Exception {
        testWithAllQualifiersSet(POL_V3);
    }

    @Test
    @Disabled("fails because of DSS bug: https://esig-dss.atlassian.net/browse/DSS-915")
    public void whenQCWithQSCDQualifierIsNotSetThenSignatureLevelShouldBeAdesQCAndInvalidWithPOLv4() {
        String policy = POL_V4;
        TrustProperties trustProperties = getServiceInfoForService(TEST_OF_KLASS3_SK_2010, policy);
        removeQcConditions(trustProperties, QC_WITH_QSCD);
        assertServiceHasQualifiers(trustProperties, QC_STATEMENT, QC_FOR_ESIG);
        SignatureValidationData signature = validateWithPolicy(policy, BDOC_TEST_OF_KLASS3_CHAIN).getValidationConclusion().getSignatures().get(0);
        assertEquals("TOTAL-FAILED", signature.getIndication());
        assertEquals("AdESQC", signature.getSignatureLevel());
    }

    @Test
    @Disabled("fails because of DSS bug: https://esig-dss.atlassian.net/browse/DSS-915")
    public void whenQCWithQSCDQualifierIsNotSetThenSignatureLevelShouldBeAdesQCAndValidWithPOLv3() {
        String policy = POL_V3;
        TrustProperties trustProperties = getServiceInfoForService(TEST_OF_KLASS3_SK_2010, policy);
        removeQcConditions(trustProperties, QC_WITH_QSCD);
        assertServiceHasQualifiers(trustProperties, QC_STATEMENT, QC_FOR_ESIG);
        SignatureValidationData signature = validateWithPolicy(policy, BDOC_TEST_OF_KLASS3_CHAIN).getValidationConclusion().getSignatures().get(0);
        assertEquals("TOTAL-PASSED", signature.getIndication());
        assertEquals("AdESQC", signature.getSignatureLevel());
    }

    @Test
    @Disabled("Unknown reason")
    public void whenQCWithQSCDAndQCStatementQualifierIsNotSetThenSignatureLevelShouldBeAdesAndInvalidWithPOLv4() {
        String policy = POL_V4;
        TrustProperties trustProperties = getServiceInfoForService(TEST_OF_KLASS3_SK_2010, policy);
        removeQcConditions(trustProperties, QC_WITH_QSCD, QC_STATEMENT);
        assertServiceHasQualifiers(trustProperties, QC_FOR_ESIG);
        SignatureValidationData signature = validateWithPolicy(policy, BDOC_TEST_OF_KLASS3_CHAIN).getValidationConclusion().getSignatures().get(0);
        assertEquals("TOTAL-FAILED", signature.getIndication());
        assertEquals("AdES", signature.getSignatureLevel());
    }

    @Disabled
    @Test
    public void whenQCWithQSCDAndQCStatementQualifierIsNotSetThenSignatureLevelShouldBeAdesAndValidWithPOLv3() {
        String policy = POL_V3;
        TrustProperties trustProperties = getServiceInfoForService(TEST_OF_KLASS3_SK_2010, policy);
        removeQcConditions(trustProperties, QC_WITH_QSCD, QC_STATEMENT);
        assertServiceHasQualifiers(trustProperties, QC_FOR_ESIG);
        SignatureValidationData signature = validateWithPolicy(policy, BDOC_TEST_OF_KLASS3_CHAIN).getValidationConclusion().getSignatures().get(0);
        assertEquals("TOTAL-PASSED", signature.getIndication());
        assertEquals("ADESIG", signature.getSignatureLevel());
    }

    @Test
    public void onlySimpleReportPresentInDocumentValidationResultReports() {
        Reports reports = timemarkContainerValidationService.validateDocument(buildValidationDocument(BDOC_TEST_FILE_ALL_SIGNED));

        assertNotNull(reports.getSimpleReport().getValidationConclusion());
        assertNotNull(reports.getDetailedReport().getValidationConclusion());
        assertNotNull(reports.getDiagnosticReport().getValidationConclusion());

        assertNull(reports.getDetailedReport().getValidationProcess());
        assertNull(reports.getDiagnosticReport().getDiagnosticData());
    }

    @Test
    public void subjectDNPresentInAllReportTypesValidationConclusion() {
        Reports reports = timemarkContainerValidationService.validateDocument(buildValidationDocument(BDOC_TEST_FILE_ALL_SIGNED));

        String expectedSerialNumber = "47711040261";
        String expectedCommonName = "SOLOVEI,JULIA,47711040261";
        String givenName = "JULIA";
        String surname = "SOLOVEI";
        assertSubjectDNPresent(reports.getSimpleReport().getValidationConclusion().getSignatures().get(0), expectedSerialNumber, expectedCommonName, givenName, surname);
        assertSubjectDNPresent(reports.getDetailedReport().getValidationConclusion().getSignatures().get(0), expectedSerialNumber, expectedCommonName, givenName, surname);
        assertSubjectDNPresent(reports.getDiagnosticReport().getValidationConclusion().getSignatures().get(0), expectedSerialNumber, expectedCommonName, givenName, surname);
    }

    @Test
    public void timeAssertionMessageImprintIsNotEmptyForLT() {
        Reports reports = timemarkContainerValidationService.validateDocument(buildValidationDocument("LT_without_nonce.bdoc"));

        SignatureValidationData signatureValidationData = reports.getSimpleReport().getValidationConclusion().getSignatures().get(0);

        assertEquals("MDEwDQYJYIZIAWUDBAIBBQAEIE541TO5ZHHgKv60XxTXJX0Qg04pjs4uN8bELnDUDFp1", signatureValidationData.getInfo().getTimeAssertionMessageImprint());
    }

    @Test
    public void timestampAndRevocationTimeExistsInLT(){
        Reports reports = timemarkContainerValidationService.validateDocument(buildValidationDocument("LT_without_nonce.bdoc"));
        SignatureValidationData signatureValidationData = reports.getSimpleReport().getValidationConclusion().getSignatures().get(0);
        Assertions.assertEquals("2020-06-04T11:34:54Z", signatureValidationData.getInfo().getOcspResponseCreationTime());
        Assertions.assertEquals("2020-06-04T11:34:53Z", signatureValidationData.getInfo().getTimestampCreationTime());
    }

    @Test
    public void timestampTimeMissingLT_TM(){
        Reports reports = timemarkContainerValidationService.validateDocument(buildValidationDocument("bdoc_tm_valid_2_signatures.bdoc"));
        SignatureValidationData signatureValidationData = reports.getSimpleReport().getValidationConclusion().getSignatures().get(0);
        Assertions.assertEquals("2020-05-21T14:07:01Z", signatureValidationData.getInfo().getOcspResponseCreationTime());
        Assertions.assertNull(signatureValidationData.getInfo().getTimestampCreationTime());
        SignatureValidationData signatureValidationData2 = reports.getSimpleReport().getValidationConclusion().getSignatures().get(1);
        Assertions.assertEquals("2020-05-28T10:59:14Z", signatureValidationData2.getInfo().getOcspResponseCreationTime());
        Assertions.assertNull(signatureValidationData2.getInfo().getTimestampCreationTime());
    }

    @Test
    public void certificatePresentLT_TM() throws Exception {
        Reports reports = timemarkContainerValidationService.validateDocument(buildValidationDocument(BDOC_TEST_FILE_ALL_SIGNED));
        SignatureValidationData signatureValidationData = reports.getSimpleReport().getValidationConclusion().getSignatures().get(0);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Assertions.assertEquals(2, signatureValidationData.getCertificates().size());

        ee.openeid.siva.validation.document.report.Certificate signerCertificate = signatureValidationData.getCertificatesByType(CertificateType.SIGNING).get(0);
        Certificate signerX509Certificate = cf.generateCertificate(new ByteArrayInputStream(Base64.decode(signerCertificate.getContent().getBytes())));
        Assertions.assertEquals("SOLOVEI,JULIA,47711040261", CertUtil.getCommonName((X509Certificate) signerX509Certificate));
        Assertions.assertEquals("SOLOVEI,JULIA,47711040261", signerCertificate.getCommonName());

        ee.openeid.siva.validation.document.report.Certificate revocationCertificate = signatureValidationData.getCertificatesByType(CertificateType.REVOCATION).get(0);
        Certificate revocationX509Certificate = cf.generateCertificate(new ByteArrayInputStream(Base64.decode(revocationCertificate.getContent().getBytes())));
        Assertions.assertEquals("SK OCSP RESPONDER 2011", CertUtil.getCommonName((X509Certificate) revocationX509Certificate));
        Assertions.assertEquals("SK OCSP RESPONDER 2011", revocationCertificate.getCommonName());
    }

    @Test
    public void certificatePresentLT() throws Exception {
        Reports reports = timemarkContainerValidationService.validateDocument(buildValidationDocument(VALID_ASICE));
        SignatureValidationData signatureValidationData = reports.getSimpleReport().getValidationConclusion().getSignatures().get(0);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Assertions.assertEquals(3, signatureValidationData.getCertificates().size());

        ee.openeid.siva.validation.document.report.Certificate signerCertificate = signatureValidationData.getCertificatesByType(CertificateType.SIGNING).get(0);
        Certificate signerX509Certificate = cf.generateCertificate(new ByteArrayInputStream(Base64.decode(signerCertificate.getContent().getBytes())));
        Assertions.assertEquals("SINIVEE,VEIKO,36706020210", CertUtil.getCommonName((X509Certificate) signerX509Certificate));
        Assertions.assertEquals("SINIVEE,VEIKO,36706020210", signerCertificate.getCommonName());

        ee.openeid.siva.validation.document.report.Certificate revocationCertificate = signatureValidationData.getCertificatesByType(CertificateType.REVOCATION).get(0);
        Certificate revocationX509Certificate = cf.generateCertificate(new ByteArrayInputStream(Base64.decode(revocationCertificate.getContent().getBytes())));
        Assertions.assertEquals("SK OCSP RESPONDER 2011", CertUtil.getCommonName((X509Certificate) revocationX509Certificate));
        Assertions.assertEquals("SK OCSP RESPONDER 2011", revocationCertificate.getCommonName());

        ee.openeid.siva.validation.document.report.Certificate timestampCertificate = signatureValidationData.getCertificatesByType(CertificateType.SIGNATURE_TIMESTAMP).get(0);
        Certificate timestampX509Certificate = cf.generateCertificate(new ByteArrayInputStream(Base64.decode(timestampCertificate.getContent().getBytes())));
        Assertions.assertEquals("SK TIMESTAMPING AUTHORITY", CertUtil.getCommonName((X509Certificate) timestampX509Certificate));
        Assertions.assertEquals("SK TIMESTAMPING AUTHORITY", timestampCertificate.getCommonName());
    }

    private void assertSubjectDNPresent(SignatureValidationData signature, String serialNumber, String
            commonName, String givenName, String surname) {
        SubjectDistinguishedName subjectDistinguishedName = signature.getSubjectDistinguishedName();
        assertNotNull(subjectDistinguishedName);
        assertEquals(serialNumber, subjectDistinguishedName.getSerialNumber());
        assertEquals(commonName, subjectDistinguishedName.getCommonName());
        assertEquals(givenName, subjectDistinguishedName.getGivenName());
        assertEquals(surname, subjectDistinguishedName.getSurname());
    }

    private void testWithAllQualifiersSet(String policy) throws Exception {
        TrustProperties trustProperties = getServiceInfoForService(TEST_OF_KLASS3_SK_2010, policy);
        assertServiceHasQualifiers(trustProperties, QC_WITH_QSCD, QC_STATEMENT, QC_FOR_ESIG);
        SignatureValidationData signature = validateWithPolicy(policy, BDOC_TEST_OF_KLASS3_CHAIN).getValidationConclusion().getSignatures().get(0);
        assertEquals("TOTAL-PASSED", signature.getIndication());
        assertEquals("QES", signature.getSignatureLevel());
    }

    private TrustProperties getServiceInfoForService(String serviceName, String policy) {
        TSLCertificateSource tslCertificateSource = configurationService.loadPolicyConfiguration(policy).getConfiguration().getTSL();
        return tslCertificateSource.getCertificates()
                .stream()
                .map(certificateToken -> getTrustProperties(tslCertificateSource, certificateToken))
                .filter(si -> serviceMatchesServiceName(si, serviceName))
                .findFirst().get();
    }

    private void assertServiceHasQualifiers(TrustProperties trustProperties, String... qualifiers) {
        List<ConditionForQualifiers> qualifiersAndConditions = trustProperties.getTrustService().getLatest().getConditionsForQualifiers();
        assertEquals(qualifiers.length, qualifiersAndConditions.size());

        Arrays.stream(qualifiers)
                .forEach(qualifier -> qualifiersAndConditions
                        .forEach(q -> assertTrue(q.getQualifiers().contains(qualifier))));
    }

    private TrustProperties getTrustProperties(TSLCertificateSource tslCertificateSource, CertificateToken
            certificateToken) {
        return (TrustProperties) tslCertificateSource.getTrustServices(certificateToken).toArray()[0];
    }

    private boolean serviceMatchesServiceName(TrustProperties trustProperties, String serviceName) {
        return trustProperties.getTrustService().getLatest().getNames().containsKey(serviceName);
    }

    private void removeQcConditions(TrustProperties trustProperties, String... qualifiers) {
        List<ConditionForQualifiers> qualifiersAndConditions = trustProperties.getTrustService().getLatest().getConditionsForQualifiers();
        Arrays.stream(qualifiers)
                .forEach(qualifier -> qualifiersAndConditions
                        .forEach(q -> q.getQualifiers().remove(qualifier)));


        qualifiersAndConditions.stream().filter(i -> i.getQualifiers().contains(qualifiers)).forEach(qualifiersAndConditions::remove);
    }

    private SimpleReport validateWithPolicy(String policyName) {
        return validateWithPolicy(policyName, VALID_BDOC_TM_2_SIGNATURES);
    }

    private SimpleReport validateWithPolicy(String policyName, String file) {
        ValidationDocument validationDocument = buildValidationDocument(file);
        validationDocument.setSignaturePolicy(policyName);
        return timemarkContainerValidationService.validateDocument(validationDocument).getSimpleReport();
    }

    private ValidationDocument bdocValid2Signatures() {
        return buildValidationDocument(VALID_BDOC_TM_2_SIGNATURES);
    }

    private ValidationDocument bdocValidIdCardAndMobIdSignatures() {
        return buildValidationDocument(VALID_ID_CARD_MOB_ID);
    }

    private ValidationDocument bdocCRLRevocationOnly() {
        return buildValidationDocument(ASICE_CRL_ONLY);
    }
}
