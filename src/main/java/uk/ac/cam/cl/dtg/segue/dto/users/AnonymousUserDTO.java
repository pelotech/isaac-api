/**
 * Copyright 2014 Stephen Cummins
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
package uk.ac.cam.cl.dtg.segue.dto.users;

import java.util.Date;
import java.util.List;
import java.util.Map;

import uk.ac.cam.cl.dtg.segue.dto.QuestionValidationResponseDTO;

import com.google.api.client.util.Maps;

/**
 * Data Transfer Object to represent an anonymous user of the system.
 * 
 */
public class AnonymousUserDTO extends AbstractSegueUserDTO {
    private String sessionId;
    private Map<String, Map<String, List<QuestionValidationResponseDTO>>> temporaryQuestionAttempts;
    private Date dateCreated;

    /**
     * Default constructor required for Jackson.
     */
    public AnonymousUserDTO() {
        temporaryQuestionAttempts = Maps.newHashMap();
    }

    /**
     * Full constructor for the AnonymousUser object.
     * 
     * @param sessionId
     *            - Our session Unique ID
     */
    public AnonymousUserDTO(final String sessionId) {
        temporaryQuestionAttempts = Maps.newHashMap();
        this.sessionId = sessionId;
    }

    /**
     * Full constructor for the AnonymousUser object.
     * 
     * @param sessionId
     *            - Our session Unique ID
     * @param temporaryQuestionAttempts
     *            - attempts.
     */
    public AnonymousUserDTO(final String sessionId,
            final Map<String, Map<String, List<QuestionValidationResponseDTO>>> temporaryQuestionAttempts) {
        this.temporaryQuestionAttempts = temporaryQuestionAttempts;
        this.sessionId = sessionId;
    }

    /**
     * Gets the sessionId.
     * 
     * @return the sessionId
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the sessionId.
     * 
     * @param sessionId
     *            the sessionId to set
     */
    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Gets the temporaryQuestionAttempts.
     * 
     * @return the temporaryQuestionAttempts
     */
    public Map<String, Map<String, List<QuestionValidationResponseDTO>>> getTemporaryQuestionAttempts() {
        return temporaryQuestionAttempts;
    }

    /**
     * Sets the temporaryQuestionAttempts.
     * 
     * @param temporaryQuestionAttempts
     *            the temporaryQuestionAttempts to set
     */
    public void setTemporaryQuestionAttempts(
            final Map<String, Map<String, List<QuestionValidationResponseDTO>>> temporaryQuestionAttempts) {
        this.temporaryQuestionAttempts = temporaryQuestionAttempts;
    }

    /**
     * Gets the dateCreated.
     * 
     * @return the dateCreated
     */
    public Date getDateCreated() {
        return dateCreated;
    }

    /**
     * Sets the dateCreated.
     * 
     * @param dateCreated
     *            the dateCreated to set
     */
    public void setDateCreated(final Date dateCreated) {
        this.dateCreated = dateCreated;
    }
}
