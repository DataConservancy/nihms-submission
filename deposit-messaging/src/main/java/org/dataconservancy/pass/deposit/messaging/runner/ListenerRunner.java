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
package org.dataconservancy.pass.deposit.messaging.runner;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.dataconservancy.pass.deposit.messaging.config.spring.JmsConfig;
import org.dataconservancy.deposit.util.async.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Import({JmsConfig.class})
public class ListenerRunner implements ApplicationContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerRunner.class);

    private static final int TEN_MINUTES = 60 * 1000 * 10;

    private ApplicationContext appCtx;

    @Bean
    ApplicationRunner runListeners(@Value("${pass.fedora.baseurl}") String fcrepoBaseUrl, OkHttpClient okHttpClient) {
        Condition<Integer> fcrepoUp = new Condition<>(() -> {
            Request get = new Request.Builder().get().url(fcrepoBaseUrl).build();
            LOG.trace(">>>> Executing GET {}", fcrepoBaseUrl);
            try (Response res = okHttpClient.newCall(get).execute()) {
                return res.code();
            }
        }, "Fedora Repository Up");

        return (args) -> {
            boolean status = fcrepoUp.awaitAndVerify(TEN_MINUTES, (code) -> code == 200);
            if (!status) {
                LOG.error("Unable to reach the Fedora server at '{}': exiting.  " +
                        "Any exceptions thrown after this message can be ignored.", fcrepoBaseUrl);
                SpringApplication.exit(appCtx, () -> 1);
            }

            LOG.info("Fedora repository is up at '{}'", fcrepoBaseUrl);
        };
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = applicationContext;
    }
}
