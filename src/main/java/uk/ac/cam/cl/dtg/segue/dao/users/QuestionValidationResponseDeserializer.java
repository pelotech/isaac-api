package uk.ac.cam.cl.dtg.segue.dao.users;

import java.io.IOException;

import org.mongojack.internal.MongoJackModule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import uk.ac.cam.cl.dtg.segue.dao.content.ChoiceDeserializer;
import uk.ac.cam.cl.dtg.segue.dao.content.ContentBaseDeserializer;
import uk.ac.cam.cl.dtg.segue.dos.QuantityValidationResponse;
import uk.ac.cam.cl.dtg.segue.dos.QuestionValidationResponse;
import uk.ac.cam.cl.dtg.segue.dos.content.Choice;
import uk.ac.cam.cl.dtg.segue.dos.content.ContentBase;

/**
 * QuestionValidationResponse deserializer
 * 
 * This class requires the primary content bas deserializer as a constructor
 * arguement.
 * 
 * It is to allow subclasses of the choices object to be detected correctly.
 */
public class QuestionValidationResponseDeserializer extends JsonDeserializer<QuestionValidationResponse> {
	private ContentBaseDeserializer contentDeserializer;
	private ChoiceDeserializer choiceDeserializer;
	
	/**
	 * Create a QuestionValidationResponse deserializer.
	 * 
	 * @param contentDeserializer
	 *            -
	 * @param choiceDeserializer
	 *            -
	 */
	public QuestionValidationResponseDeserializer(final ContentBaseDeserializer contentDeserializer,
			final ChoiceDeserializer choiceDeserializer) {
		this.contentDeserializer = contentDeserializer;
		this.choiceDeserializer = choiceDeserializer;
	}

	@Override
	public QuestionValidationResponse deserialize(final JsonParser jsonParser,
			final DeserializationContext deserializationContext) throws IOException {

		SimpleModule contentDeserializerModule = new SimpleModule(
				"ContentDeserializerModule");
		contentDeserializerModule.addDeserializer(ContentBase.class,
				contentDeserializer);
		contentDeserializerModule.addDeserializer(Choice.class, choiceDeserializer);

		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(contentDeserializerModule);
		
		MongoJackModule.configure(mapper);
		
		ObjectNode root = (ObjectNode) mapper.readTree(jsonParser);

		if (null == root.get("answer")) {
			throw new JsonMappingException(
					"Error: unable to parse content as there is no answer property within the json input.");			
		}

		// Have to get the raw json out otherwise we dates do not serialize properly. 		
		String jsonString = new ObjectMapper().writeValueAsString(root);
		String questionResponseType = root.get("answer").get("type").textValue();
		if (questionResponseType.equals("quantity")) {
			return mapper.readValue(jsonString, QuantityValidationResponse.class);
		} else {
			return mapper.readValue(jsonString, QuestionValidationResponse.class);
		}
	}
}