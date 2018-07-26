/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.deposit.messaging.config.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.abdera.parser.Parser;
import org.apache.abdera.parser.stax.FOMParserFactory;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.commons.httpclient.Credentials;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsAssembler;
import org.dataconservancy.pass.deposit.builder.fs.FcrepoModelBuilder;
import org.dataconservancy.pass.deposit.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.pass.deposit.messaging.config.repository.Repositories;
import org.dataconservancy.pass.deposit.messaging.config.repository.SwordV2Binding;
import org.dataconservancy.pass.deposit.messaging.status.RepositoryCopyStatusMapper;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.ftp.FtpTransport;
import org.dataconservancy.pass.client.PassClientDefault;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.deposit.assembler.dspace.mets.DspaceMetsAssembler;
import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.InMemoryMapRegistry;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.DirtyDepositPolicy;
import org.dataconservancy.pass.deposit.messaging.service.DepositTask;
import org.dataconservancy.pass.deposit.messaging.status.AbderaDepositStatusRefProcessor;
import org.dataconservancy.pass.deposit.messaging.status.AtomFeedStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomFeedStatusParser;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Base64.getEncoder;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_USERNAME;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Configuration
@EnableAutoConfiguration(exclude = {RestTemplateAutoConfiguration.class})
@Import(RepositoriesFactoryBeanConfig.class)
public class DepositConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DepositConfig.class);

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    @Value("${pass.fedora.user}")
    private String fedoraUser;

    @Value("${pass.fedora.password}")
    private String fedoraPass;

    @Value("${pass.fedora.baseurl}")
    private String fedoraBaseUrl;

    @Value("${pass.elasticsearch.url}")
    private String esUrl;

    @Value("${pass.elasticsearch.limit}")
    private int esLimit;

    @Value("${pass.deposit.workers.concurrency}")
    private int depositWorkersConcurrency;

    @Value("${pass.deposit.http.agent}")
    private String passHttpAgent;

    @Value("${pass.deposit.repository.configuration}")
    private Resource repositoryConfigResource;

    @Bean
    public PassClientDefault passClient() {

        // PassClientDefault can't be injected with configuration; requires system properties be set.
        // If a system property is already set, allow it to override what is resolved by the Spring environment.
        if (!System.getProperties().containsKey("pass.fedora.user")) {
            System.setProperty("pass.fedora.user", fedoraUser);
        }

        if (!System.getProperties().containsKey("pass.fedora.password")) {
            System.setProperty("pass.fedora.password", fedoraPass);
        }

        if (!System.getProperties().containsKey("pass.fedora.baseurl")) {
            System.setProperty("pass.fedora.baseurl", fedoraBaseUrl);
        }

        if (!System.getProperties().containsKey("pass.elasticsearch.url")) {
            System.setProperty("pass.elasticsearch.url", esUrl);
        }

        if (!System.getProperties().containsKey("pass.elasticsearch.limit")) {
            System.setProperty("pass.elasticsearch.limit", String.valueOf(esLimit));
        }

        if (!System.getProperties().containsKey("http.agent")) {
            System.setProperty("http.agent", passHttpAgent);
        }

        return new PassClientDefault();
    }

    @Bean
    public PassJsonAdapterBasic passJsonAdapter() {
        return new PassJsonAdapterBasic();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public FcrepoModelBuilder fcrepoModelBuilder() {
        return new FcrepoModelBuilder();
    }

    @Bean
    public FilesystemModelBuilder fileSystemModelBuilder() {
        return new FilesystemModelBuilder();
    }

    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        String builderName = builder.getClass().getSimpleName();
        String builderHashcode = toHexString(identityHashCode(builder.getClass()));

        if (fedoraUser != null) {
            LOG.trace(">>>> {}:{} adding Authorization interceptor", builderName, builderHashcode);
            builder.addInterceptor((chain) -> {
                Request request = chain.request();
                if (!request.url().toString().startsWith(fedoraBaseUrl)) {
                    return chain.proceed(request);
                }
                Request.Builder reqBuilder = request.newBuilder();
                byte[] bytes = String.format("%s:%s", fedoraUser, fedoraPass).getBytes();
                return chain.proceed(reqBuilder
                        .addHeader("Authorization",
                                "Basic " + getEncoder().encodeToString(bytes)).build());
            });
        }

        LOG.trace(">>>> {}:{} adding Accept interceptor", builderName, builderHashcode);
        builder.addInterceptor((chain) -> {
            Request request = chain.request();
            if (!request.url().toString().startsWith(fedoraBaseUrl)) {
                return chain.proceed(request);
            }
            Request.Builder reqBuilder = request.newBuilder();
            return chain.proceed(reqBuilder
                    .addHeader("Accept", "application/ld+json").build());
        });

        if (LOG.isDebugEnabled()) {
            LOG.trace(">>>> {}:{} adding Logging interceptor", builderName, builderHashcode);
            HttpLoggingInterceptor httpLogger = new HttpLoggingInterceptor(LOG::debug);
            builder.addInterceptor(httpLogger);
        }

        LOG.trace(">>>> {}:{} adding User-Agent interceptor", builderName, builderHashcode);
        builder.addInterceptor((chain) -> {
            Request.Builder reqBuilder = chain.request().newBuilder();
            reqBuilder.removeHeader("User-Agent");
            reqBuilder.addHeader("User-Agent", passHttpAgent);
            return chain.proceed(reqBuilder.build());
        });

        OkHttpClient client = builder.build();
        LOG.trace(">>>> {}:{} built OkHttpClient {}:{}", builderName, builderHashcode,
                client.getClass().getSimpleName(), toHexString(identityHashCode(client.getClass())));

        return client;
    }

    @Bean
    public Registry<Packager> packagerRegistry(Map<String, Packager> packagers) {
        return new InMemoryMapRegistry<>(packagers);
    }

    // TODO: assemble packagers map dynamically from the repositories.json
    @Bean
    public Map<String, Packager> packagers(Map<String, Assembler> assemblers, Map<String, Transport> transports,
                                           Repositories repositories,
                                           AbderaDepositStatusRefProcessor abderaDepositStatusRefProcessor) {
        Map<String, Packager> packagers = new HashMap<>();
        // TODO: transport registries looked up by hard-coded strings.  Need a more reliable way of discovering repositories, the packagers for those repositories, and their configuration
        packagers.put("JScholarship",
                new Packager("JScholarship",
                        assemblers.get(
                                repositories.getConfig("J10P").getAssemblerConfig().getSpec()),
                        transports.get(
                                repositories.getConfig("J10P")
                                        .getTransportConfig().getProtocolBinding().getProtocol()),
                        repositories.getConfig("J10P"),
                        abderaDepositStatusRefProcessor));
        packagers.put("PubMed Central",
                new Packager("PubMed Central",
                        assemblers.get(
                                repositories.getConfig("PubMed").getAssemblerConfig().getSpec()),
                        transports.get(
                                repositories.getConfig("PubMed")
                                        .getTransportConfig().getProtocolBinding().getProtocol()),
                repositories.getConfig("PubMed")));
        return packagers;
    }

    // TODO: discover Assemblers on the classpath
    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Bean
    public Map<String, Assembler> assemblers(DspaceMetsAssembler dspaceMetsAssembler, NihmsAssembler nihmsAssembler) {
        return new HashMap<String, Assembler>() {
            {
                put(DspaceMetsAssembler.SPEC_DSPACE_METS, dspaceMetsAssembler);
                put(NihmsAssembler.SPEC_NIHMS_NATIVE_2017_07, nihmsAssembler);
            }
        };
    }

    // TODO: discover Transports on the classpath
    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Bean
    public Map<String, Transport> transports(Sword2Transport sword2Transport, FtpTransport ftpTransport) {
        return new HashMap<String, Transport>() {
            {
                put(Sword2Transport.PROTOCOL.swordv2.name(), sword2Transport);
                put(FtpTransport.PROTOCOL.ftp.name(), ftpTransport);
            }
        };
    }

    @Bean
    public DocumentBuilderFactory dbf() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf;
    }

    @Bean
    public ThreadPoolTaskExecutor depositWorkers(DepositServiceErrorHandler errorHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(depositWorkersConcurrency);
        executor.setQueueCapacity(10);
        executor.setRejectedExecutionHandler((rejectedTask, exe) -> {
            String msg = String.format(">>>> Task %s@%s rejected, will be retried later.",
                    rejectedTask.getClass().getSimpleName(), toHexString(identityHashCode(rejectedTask)));
            if (rejectedTask instanceof DepositTask && ((DepositTask)rejectedTask).getDepositWorkerContext() != null) {
                DepositServiceRuntimeException ex = new DepositServiceRuntimeException(msg, ((DepositTask)
                        rejectedTask).getDepositWorkerContext().deposit());
                errorHandler.handleError(ex);
            } else {
                LOG.error(msg);
            }
        });

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("Deposit-Worker-");
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("Deposit-Worker-" + THREAD_COUNTER.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, throwable) -> errorHandler.handleError(throwable));
            return t;
        };
        executor.setThreadFactory(tf);
        return executor;
    }

    // TODO: each SWORDv2 binding will need an AbderaClient
    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Bean
    public AbderaClient abderaClient(Repositories repositories) {
        AbderaClient ac = new AbderaClient();

        SwordV2Binding swordV2Binding = (SwordV2Binding) repositories.getConfig("J10P")
                .getTransportConfig().getProtocolBinding();

        if (swordV2Binding.getUsername() != null && swordV2Binding.getUsername().trim().length() > 0) {

            Credentials creds = new org.apache.commons.httpclient.UsernamePasswordCredentials(
                    swordV2Binding.getUsername(),
                    swordV2Binding.getPassword()
            );

            try {
                ac.addCredentials(null, null, null, creds);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return ac;
    }

    // TODO: each SWORDv2 binding will need an AtomFeedStatusParser
    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Bean
    public AtomFeedStatusParser atomFeedStatusParser(Repositories repositories, Parser abderaParser) {
        AtomFeedStatusParser feedStatusParser = new AtomFeedStatusParser(abderaParser);
        RepositoryConfig repositoryConfig = repositories.getConfig("J10P");
        SwordV2Binding swordV2Binding = (SwordV2Binding) repositoryConfig.getTransportConfig().getProtocolBinding();
        feedStatusParser.setSwordUsername(swordV2Binding.getUsername());
        feedStatusParser.setSwordPassword(swordV2Binding.getPassword());
        return feedStatusParser;
    }

    // TODO: convert to using RepositoryConfig
    @Bean
    public AtomFeedStatusMapper swordv2DspaceStatusMapper(@Value("${pass.deposit.status.mapping}")
                                                               Resource depositMappingResource,
                                                          ObjectMapper objectMapper) {
        try {
            return new AtomFeedStatusMapper(objectMapper.readTree(depositMappingResource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException("Error reading deposit status map resource " + depositMappingResource + ": " +
                    e.getMessage(), e);
        }
    }

    // TODO: convert to using RepositoryConfig
    @Bean
    public SwordDspaceDepositStatusMapper swordDspaceDepositStatusMapper(@Value("${pass.deposit.status.mapping}")
                                                                                     Resource depositMappingResource,
                                                                         ObjectMapper objectMapper) {
        try {
            return new SwordDspaceDepositStatusMapper(objectMapper.readTree(depositMappingResource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException("Error reading deposit status map resource " + depositMappingResource + ": " +
                    e.getMessage(), e);
        }
    }

    @Bean
    public RepositoryCopyStatusMapper repoCopyv2StatusMapper(@Value("${pass.deposit.status.mapping}")
                                                                     Resource depositMappingResource,
                                                             ObjectMapper objectMapper) {
        try {
            return new RepositoryCopyStatusMapper(objectMapper.readTree(depositMappingResource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException("Error reading deposit status map resource " + depositMappingResource + ": " +
                    e.getMessage(), e);
        }
    }

    @Bean
    DirtyDepositPolicy dirtyDepositPolicy() {
        return new DirtyDepositPolicy();
    }

    @Bean
    Parser abderaParser() {
        return new FOMParserFactory().getParser();
    }

    @Bean
    @SuppressWarnings("SpringJavaAutowiringInspection")
    DepositServiceErrorHandler errorHandler(CriticalRepositoryInteraction cri) {
        return new DepositServiceErrorHandler(cri);
    }

}
