/*
 * Copyright 2021 Chris Purdy
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
package uk.ac.cam.cl.dtg.isaac.dto;

import uk.ac.cam.cl.dtg.isaac.dos.content.JsonContentType;

/**
 * Content DTO for IsaacClozeQuestions.
 *
 */
@JsonContentType("isaacClozeQuestion")
public class IsaacClozeQuestionDTO extends IsaacItemQuestionDTO {

    private Boolean withReplacement;

    public Boolean getWithReplacement() {
        return withReplacement;
    }

    public void setWithReplacement(final Boolean withReplacement) {
        this.withReplacement = withReplacement;
    }
}
