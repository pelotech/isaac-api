/*
 * Copyright 2015 Stephen Cummins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.cam.cl.dtg.segue.api.monitors;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.segue.api.Constants;
import uk.ac.cam.cl.dtg.segue.comm.EmailCommunicationMessage;
import uk.ac.cam.cl.dtg.segue.comm.EmailManager;
import uk.ac.cam.cl.dtg.segue.comm.EmailType;
import uk.ac.cam.cl.dtg.util.PropertiesLoader;

import static uk.ac.cam.cl.dtg.segue.api.Constants.*;

/**
 * Handler to deal with token ownership requests.
 * 
 * Preventing users from overusing this endpoint is important as some email address information is exposed for identity
 * verification purposes.
 *
 */
public class TokenOwnerLookupMisuseHandler implements IMisuseHandler {
    private static final Logger log = LoggerFactory.getLogger(TokenOwnerLookupMisuseHandler.class);

    public static final Integer SOFT_THRESHOLD = 20;
    public static final Integer HARD_THRESHOLD = 50;
    public static final Integer ACCOUNTING_INTERVAL = NUMBER_SECONDS_IN_ONE_DAY;

    private final PropertiesLoader properties;
    private final EmailManager emailManager;

    /**
     * @param emailManager
     *            - so we can send e-mails if the threshold limits have been reached.
     * @param properties
     *            - so that we can look up properties set.
     */
    @Inject
    public TokenOwnerLookupMisuseHandler(final EmailManager emailManager, final PropertiesLoader properties) {
        this.properties = properties;
        this.emailManager = emailManager;
    }

    @Override
    public Integer getSoftThreshold() {
        return SOFT_THRESHOLD;
    }

    /*
     * (non-Javadoc)
     * 
     * @see uk.ac.cam.cl.dtg.segue.api.managers.IMisuseEvent#getHardThreshold()
     */
    @Override
    public Integer getHardThreshold() {
        return HARD_THRESHOLD;
    }

    @Override
    public Integer getAccountingIntervalInSeconds() {
        return ACCOUNTING_INTERVAL;
    }

    @Override
    public void executeSoftThresholdAction(final String message) {
        log.warn("Soft threshold limit: " + message);

    }

    @Override
    public void executeHardThresholdAction(final String message) {
        final String subject = "HARD Threshold limit reached for TokenOwnershipRequest endpoint";

        EmailCommunicationMessage e = new EmailCommunicationMessage(properties.getProperty(Constants.SERVER_ADMIN_ADDRESS),
                subject, message, message, EmailType.ADMIN);
        emailManager.addSystemEmailToQueue(e);
        log.warn("Hard threshold limit: " + message);

    }
}
