/*
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
package uk.ac.cam.cl.dtg.segue.dao.content;

import ma.glasnost.orika.MappingContext;
import ma.glasnost.orika.metadata.Type;
import uk.ac.cam.cl.dtg.isaac.dos.content.ChemicalFormula;
import uk.ac.cam.cl.dtg.isaac.dos.content.Choice;
import uk.ac.cam.cl.dtg.isaac.dos.content.Formula;
import uk.ac.cam.cl.dtg.isaac.dos.content.FreeTextRule;
import uk.ac.cam.cl.dtg.isaac.dos.content.GraphChoice;
import uk.ac.cam.cl.dtg.isaac.dos.content.ItemChoice;
import uk.ac.cam.cl.dtg.isaac.dos.content.LogicFormula;
import uk.ac.cam.cl.dtg.isaac.dos.content.ParsonsChoice;
import uk.ac.cam.cl.dtg.isaac.dos.content.Quantity;
import uk.ac.cam.cl.dtg.isaac.dos.content.StringChoice;
import uk.ac.cam.cl.dtg.isaac.dos.content.RegexPattern;
import uk.ac.cam.cl.dtg.isaac.dto.content.ChemicalFormulaDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.ChoiceDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.FormulaDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.FreeTextRuleDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.GraphChoiceDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.ItemChoiceDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.LogicFormulaDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.ParsonsChoiceDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.QuantityDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.StringChoiceDTO;
import uk.ac.cam.cl.dtg.isaac.dto.content.RegexPatternDTO;

/**
 * ContentBaseOrikaConverter A specialist converter class to work with the Orika automapper library.
 * 
 * Responsible for converting Choice objects to their correct subtype.
 * 
 */
public class ChoiceOrikaConverter extends AbstractPolymorphicBidirectionalConverter<Choice, ChoiceDTO> {

    /**
     * Constructs an Orika Converter specialises in selecting the correct subclass for choice objects.
     * 
     */
    public ChoiceOrikaConverter() {

    }

    @Override
    public ChoiceDTO convertTo(final Choice source, final Type<ChoiceDTO> destinationType,
                               MappingContext _context) {
        if (null == source) {
            return null;
        }

        if (source instanceof Quantity) {
            return super.mapperFacade.map(source, QuantityDTO.class);
        } else if (source instanceof Formula) {
            return super.mapperFacade.map(source, FormulaDTO.class);
        } else if (source instanceof ChemicalFormula) {
            return super.mapperFacade.map(source, ChemicalFormulaDTO.class);
        } else if (source instanceof LogicFormula) {
            return super.mapperFacade.map(source, LogicFormulaDTO.class);
        } else if (source instanceof GraphChoice) {
            return super.mapperFacade.map(source, GraphChoiceDTO.class);
        } else if (source instanceof StringChoice) {
            return super.mapperFacade.map(source, StringChoiceDTO.class);
        } else if (source instanceof RegexPattern) {
            return super.mapperFacade.map(source, RegexPatternDTO.class);
        } else if (source instanceof FreeTextRule) {
            return super.mapperFacade.map(source, FreeTextRuleDTO.class);
        } else if (source instanceof ParsonsChoice) {
            return super.mapperFacade.map(source, ParsonsChoiceDTO.class);
        } else if (source instanceof ItemChoice) {
            return super.mapperFacade.map(source, ItemChoiceDTO.class);
        } else {
            // I would have expected this to cause an infinite loop / stack
            // overflow but apparently it doesn't.
            ChoiceDTO choiceDTO = new ChoiceDTO();
            super.mapperFacade.map(source, choiceDTO);
            return choiceDTO;
        }
    }

    @Override
    public Choice convertFrom(final ChoiceDTO source, final Type<Choice> destinationType,
                              MappingContext _context) {
        if (null == source) {
            return null;
        }

        if (source instanceof QuantityDTO) {
            return super.mapperFacade.map(source, Quantity.class);
        } else if (source instanceof FormulaDTO) {
            return super.mapperFacade.map(source, Formula.class);
        } else if (source instanceof ChemicalFormulaDTO) {
            return super.mapperFacade.map(source, ChemicalFormula.class);
        } else if (source instanceof LogicFormulaDTO) {
            return super.mapperFacade.map(source, LogicFormula.class);
        } else if (source instanceof GraphChoiceDTO) {
            return super.mapperFacade.map(source, GraphChoice.class);
        } else if (source instanceof StringChoiceDTO) {
            return super.mapperFacade.map(source, StringChoice.class);
        } else if (source instanceof RegexPatternDTO) {
            return super.mapperFacade.map(source, RegexPattern.class);
        } else if (source instanceof FreeTextRuleDTO) {
            return super.mapperFacade.map(source, FreeTextRule.class);
        } else if (source instanceof ParsonsChoiceDTO) {
            return super.mapperFacade.map(source, ParsonsChoice.class);
        } else if (source instanceof ItemChoiceDTO) {
            return super.mapperFacade.map(source, ItemChoice.class);
        } else {
            // I would have expected this to cause an infinite loop / stack
            // overflow but apparently it doesn't.
            Choice choice = new Choice();
            super.mapperFacade.map(source, choice);
            return choice;
        }
    }
}
