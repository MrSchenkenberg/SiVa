/*
 * Copyright 2017 Riigi Infosüsteemide Amet
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

package ee.openeid.siva.signature;

import ee.openeid.siva.signature.configuration.Pkcs12Properties;
import ee.openeid.siva.signature.configuration.SignatureServiceConfigurationProperties;
import ee.openeid.siva.signature.exception.SignatureServiceException;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AsiceWithXadesSignatureServiceTest {

    AsiceWithXadesSignatureService asiceSignatureService;

    @BeforeEach
    public void setUp() {

        SignatureServiceConfigurationProperties properties = new SignatureServiceConfigurationProperties();
        properties.setSignatureLevel("XAdES_BASELINE_B");
        properties.setTspUrl("http://demo.sk.ee/tsa");
        properties.setOcspUrl("http://demo.sk.ee/ocsp");
        Pkcs12Properties pkcs12Properties = new Pkcs12Properties();
        pkcs12Properties.setPath("classpath:sign_ESTEID2018.p12");
        pkcs12Properties.setPassword("1234");
        properties.setPkcs12(pkcs12Properties);
        TrustedListsCertificateSource trustedListsCertificateSource = new TrustedListsCertificateSource();
        asiceSignatureService = new AsiceWithXadesSignatureService(properties, trustedListsCertificateSource);
    }

    @Test
    public void AsiceSignatureServiceNotConfiguredWithProperties_shouldThrowException() throws IOException {
        Assertions.assertThrows(SignatureServiceException.class, () -> {
            asiceSignatureService = new AsiceWithXadesSignatureService(null, new TrustedListsCertificateSource());
            asiceSignatureService.getSignature("Hello".getBytes(), "hello.txt", "application/text");
        }, "Signature configuration properties not set!");
    }

    @Test
    public void AsiceSignatureServiceNotConfiguredWithPkcs12Properties_shouldThrowException() throws IOException {
        Assertions.assertThrows(SignatureServiceException.class, () -> {
            asiceSignatureService.getProperties().setPkcs12(null);
            asiceSignatureService.getSignature("Hello".getBytes(), "hello.txt", "application/text");
        }, "Either Pkcs11 or Pkcs12 must be configured! Currently there is none configured..");
    }

    @Test
    public void AsiceSignatureServiceConfiguredWithInvalidPkcs12Keystore_shouldThrowException() throws IOException {
        Assertions.assertThrows(SignatureServiceException.class, () -> {
            asiceSignatureService.getProperties().getPkcs12().setPath("classpath:invalid.p12");
            asiceSignatureService.getSignature("Hello".getBytes(), "hello.txt", "application/text");
        }, "Error reading keystore from path: ");
    }

    @Test
    public void AsiceSignatureServiceConfiguredWithInvalidPkcs12Password_shouldThrowException() throws IOException {
        Assertions.assertThrows(DSSException.class, () -> {
            asiceSignatureService.getProperties().getPkcs12().setPassword("invalid password");
            asiceSignatureService.getSignature("Hello".getBytes(), "hello.txt", "application/text");
        }, "Unable to instantiate KeyStoreSignatureTokenConnection");
    }

    @Test
    public void AsiceSignatureServiceConfiguredWithInvalidSignatureLevel_shouldThrowException() throws IOException {
        Assertions.assertThrows(SignatureServiceException.class, () -> {
            asiceSignatureService.getProperties().setSignatureLevel("SOME_INVALID_LEVEL");
            asiceSignatureService.getSignature("Hello".getBytes(), "hello.txt", "application/text");
        }, "Invalid signature level - 'SOME_INVALID_LEVEL'! Valid signature levels:");
    }

    @Test
    public void AsiceSignatureServiceConfiguredCorrectly_withXadesBaselineProfileB_shouldPass() throws IOException {
        byte[] signature = asiceSignatureService.getSignature("Hello".getBytes(), "hello.txt", "application/text");
        assertNotNull(signature);
    }

} 
