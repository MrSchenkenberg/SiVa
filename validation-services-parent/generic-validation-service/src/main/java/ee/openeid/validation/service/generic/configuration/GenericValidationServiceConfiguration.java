/*
 * Copyright 2016 - 2021 Riigi Infosüsteemi Amet
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

package ee.openeid.validation.service.generic.configuration;

import ee.openeid.siva.validation.service.signature.policy.ConstraintLoadingSignaturePolicyService;
import ee.openeid.validation.service.generic.validator.container.ContainerValidator;
import ee.openeid.validation.service.generic.validator.container.ContainerValidatorFactory;
import ee.openeid.validation.service.generic.validator.container.AsicContainerDataFileSizeValidator;
import ee.openeid.validation.service.generic.validator.container.ZipBasedContainerValidator;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.validation.reports.AbstractReports;
import eu.europa.esig.dss.validation.reports.Reports;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@EnableConfigurationProperties(GenericSignaturePolicyProperties.class)
public class GenericValidationServiceConfiguration {

    @Bean(name = "GenericPolicyService")
    public ConstraintLoadingSignaturePolicyService signaturePolicyService(GenericSignaturePolicyProperties properties) {
        return new ConstraintLoadingSignaturePolicyService(properties);
    }

    @Bean
    public ContainerValidatorFactory containerValidatorFactory() {
        return (validationReports, validationDocument) -> isAsicContainer(validationReports)
                ? new ZipBasedContainerValidator(validationDocument, new AsicContainerDataFileSizeValidator(validationReports))
                : ContainerValidator.NO_OP_INSTANCE;
    }

    private static boolean isAsicContainer(Reports validationReports) {
        return Optional.ofNullable(validationReports)
                .map(AbstractReports::getDiagnosticData)
                .map(DiagnosticData::getContainerType)
                .filter(ASiCContainerType.class::isInstance)
                .isPresent();
    }

}
