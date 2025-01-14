/**
 * Copyright 2014 Ian Davies
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
package uk.ac.cam.cl.dtg.isaac.dto.content;

public class ChemicalFormulaDTO extends ChoiceDTO {

    private String mhchemExpression;

    /**
     * Default constructor required for mapping.
     */
    public ChemicalFormulaDTO() {}

    /**
     * Gets the mhchem expression.
     *
     * @return the mhchem expression
     */
    public final String getMhchemExpression() {
        return mhchemExpression;
    }

    /**
     * Sets the mhchem expression.
     *
     * @param mhchemExpression
     *            the python expression to set
     */
    public final void setMhchemExpression(final String mhchemExpression) {
        this.mhchemExpression = mhchemExpression;
    }
}
