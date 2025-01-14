/*
 * Copyright 2020 Meurig Thomas
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
package uk.ac.cam.cl.dtg.isaac.dos;

/**
 * Data structure to hold the expected correctness of a user's answer.
 */
public class TestCase extends QuestionValidationResponse {
    private Boolean expected;

    public void setExpected(Boolean expected) {
        this.expected = expected;
    }
    public Boolean getExpected() {
        return this.expected;
    }
}
