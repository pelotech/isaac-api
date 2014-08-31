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
package uk.ac.cam.cl.dtg.segue.dao.content;

import uk.ac.cam.cl.dtg.segue.dos.content.Choice;
import uk.ac.cam.cl.dtg.segue.dos.content.Quantity;
import uk.ac.cam.cl.dtg.segue.dto.content.ChoiceDTO;
import uk.ac.cam.cl.dtg.segue.dto.content.QuantityDTO;
import ma.glasnost.orika.CustomConverter;
import ma.glasnost.orika.metadata.Type;

/**
 * ContentBaseOrikaConverter A specialist converter class to work with the Orika
 * automapper library.
 * 
 * Responsible for converting Choice objects to their correct subtype.
 * 
 */
public class ChoiceOrikaConverter extends CustomConverter<Choice, ChoiceDTO> {

	/**
	 * Constructs an Orika Converter specialises in selecting the correct
	 * subclass for choice objects.
	 * 
	 */
	public ChoiceOrikaConverter() {
		
	}

	@Override
	public ChoiceDTO convert(final Choice source,
			final Type<? extends ChoiceDTO> destinationType) {
		if (null == source) {
			return null;
		}

		if (source instanceof Quantity) {
			return super.mapperFacade.map(source, QuantityDTO.class);
		} else {
			// I would have expected this to cause an infinite loop / stack
			// overflow but apparently it doesn't.
			ChoiceDTO choiceDTO = new ChoiceDTO();
			super.mapperFacade.map(source, choiceDTO);
			return choiceDTO;
		}
	}
}
