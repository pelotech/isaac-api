/*
 * Copyright 2022 Matthew Trew
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
package uk.ac.cam.cl.dtg.segue.search;

/**
 * SimpleExclusionInstruction
 *
 * SimpleExclusionInstruction expect a single value which must not appear in the results.
 */
public class SimpleExclusionInstruction extends AbstractFilterInstruction {
    private final String mustNotMatchValue;

    public SimpleExclusionInstruction(String mustNotMatchValue) {

        this.mustNotMatchValue = mustNotMatchValue;
    }

    /**
     * Get the value which must not be matched.
     * @return the value to exclude.
     */
    public String getMustNotMatchValue() {
        return mustNotMatchValue;
    }
}
