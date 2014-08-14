package uk.ac.cam.cl.dtg.segue.dao.schools;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import uk.ac.cam.cl.dtg.segue.api.Constants;
import uk.ac.cam.cl.dtg.segue.dos.users.School;
import uk.ac.cam.cl.dtg.segue.search.ISearchProvider;
import static uk.ac.cam.cl.dtg.segue.api.Constants.*;

/**
 * Class responsible for reading the local school list csv file.
 * 
 */
public class SchoolListReader {
	private static final Logger log = LoggerFactory.getLogger(SchoolListReader.class);

	private final String fileToLoad;
	private final ISearchProvider searchProvider;

	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * SchoolListReader constructor.
	 * 
	 * @param filename
	 *            - csv file containing the list of schools.
	 * @param searchProvider
	 *            - search provider that can be used to put and retrieve school
	 *            data.
	 */
	@Inject
	public SchoolListReader(@Named(Constants.SCHOOL_CSV_LIST_PATH) final String filename,
			final ISearchProvider searchProvider) {
		this.fileToLoad = filename;
		this.searchProvider = searchProvider;
	}

	/**
	 * findSchoolByNameOrPostCode.
	 * 
	 * @param searchQuery
	 *            - school to search for - either name or postcode.
	 * @return list of schools matching the criteria or an empty list.
	 * @throws UnableToIndexSchoolsException
	 *             - if there is an error access the index of schools.
	 */
	public List<School> findSchoolByNameOrPostCode(final String searchQuery)
		throws UnableToIndexSchoolsException {
		if (!this.ensureSchoolList()) {
			log.error("Unable to ensure school search cache.");
			throw new UnableToIndexSchoolsException("unable to ensure the cache has been populated");
		}

		List<String> schoolSearchResults = searchProvider.fuzzySearch(SCHOOLS_SEARCH_INDEX,
				SCHOOLS_SEARCH_TYPE, searchQuery, null, Constants.SCHOOL_URN_FIELDNAME_POJO, 
				Constants.SCHOOL_ESTABLISHMENT_NAME_FIELDNAME_POJO,
				Constants.SCHOOL_POSTCODE_FIELDNAME_POJO).getResults();

		List<School> resultList = Lists.newArrayList();
		for (String schoolString : schoolSearchResults) {
			try {
				resultList.add(mapper.readValue(schoolString, School.class));
			} catch (JsonParseException | JsonMappingException e) {
				log.error("Unable to parse the school " + schoolString, e);
			} catch (IOException e) {
				log.error("IOException " + schoolString, e);
			}
		}
		return resultList;
	}

	/**
	 * Trigger a thread to index the schools list. If needed.
	 */
	public synchronized void prepareSchoolList() {
		// if the search provider has the index just return.
		if (searchProvider.hasIndex(SCHOOLS_SEARCH_INDEX)) {
			return;
		} 

		Thread thread = new Thread() {
			public void run() {
				log.info("Starting a new thread to index schools list.");
				try {
					indexSchoolsWithSearchProvider();
				} catch (UnableToIndexSchoolsException e) {
					log.error("Unable to index the schools list.");
				}
			}
		};
		
		thread.start();
	}
	
	/**
	 * Ensure School List has been generated.
	 * 
	 * @return true if we have an index or false if not. If false we cannot
	 *         guarantee a response.
	 * @throws UnableToIndexSchoolsException
	 *             - If there is a problem indexing.
	 */
	private boolean ensureSchoolList() throws UnableToIndexSchoolsException {
		if (searchProvider.hasIndex(SCHOOLS_SEARCH_INDEX)) {
			return true;
		} else {
			this.indexSchoolsWithSearchProvider();
		}

		return searchProvider.hasIndex(SCHOOLS_SEARCH_INDEX);
	}

	/**
	 * Build the index for the search schools provider.
	 * 
	 * @throws UnableToIndexSchoolsException
	 *             - when there is a problem building the index of schools.
	 */
	private synchronized void indexSchoolsWithSearchProvider() throws UnableToIndexSchoolsException {
		if (!searchProvider.hasIndex(SCHOOLS_SEARCH_INDEX)) {
			log.info("Creating schools index with search provider.");
			List<School> schoolList = this.loadAndBuildSchoolList();

			for (School school : schoolList) {
				try {
					searchProvider.indexObject(SCHOOLS_SEARCH_INDEX, SCHOOLS_SEARCH_TYPE,
							mapper.writeValueAsString(school));
				} catch (JsonProcessingException e) {
					log.error("Unable to serialize the school object into json.", e);
				}
			}
			log.info("School list indexing complete.");
		} else {
			log.info("Cancelling school search index operation as another thread has already done it.");
		}
	}
	
	/**
	 * Loads the school list from the preconfigured filename.
	 * 
	 * @return the list of schools.
	 * @throws UnableToIndexSchoolsException
	 *             - when there is a problem indexing.
	 */
	private synchronized List<School> loadAndBuildSchoolList() throws UnableToIndexSchoolsException {
		// otherwise we need to generate it.
		List<School> schools = Lists.newArrayList();

		try {
			BufferedReader reader = new BufferedReader(new FileReader(fileToLoad));
			String line = null;

			// use first line to determine field names.
			String[] columns = reader.readLine().split(",");

			Map<String, Integer> fieldNameMapping = new TreeMap<String, Integer>();

			for (int i = 0; i < columns.length; i++) {
				fieldNameMapping.put(columns[i].trim().replace("\"", ""), i);
			}

			// we expect the columns to have the followings:
			// SCHOOL URN | EstablishmentNumber | EstablishmentName | Town
			// Postcode
			while ((line = reader.readLine()) != null && !line.isEmpty()) {
				// we have to remove the quotes from the string as the source file is ugly.
				line = line.replace("\"", "");
				String[] schoolArray = line.split(",");
				try {
					School schoolToSave = new School(
							schoolArray[fieldNameMapping.get(Constants.SCHOOL_URN_FIELDNAME)],
							schoolArray[fieldNameMapping.get(Constants.SCHOOL_ESTABLISHMENT_NUMBER_FIELDNAME)],
							schoolArray[fieldNameMapping.get(Constants.SCHOOL_ESTABLISHMENT_NAME_FIELDNAME)],
							null);

					// check if school has a post code as some of them do not.
					if (schoolArray.length - 1 == fieldNameMapping.get(Constants.SCHOOL_POSTCODE_FIELDNAME)) {
						schoolToSave.setPostcode(schoolArray[fieldNameMapping
								.get(Constants.SCHOOL_POSTCODE_FIELDNAME)]);
					}

					schools.add(schoolToSave);
				} catch (IndexOutOfBoundsException e) {
					// this happens when the school does not have the required
					// data
					log.warn("Unable to load the following school into the school list due to missing required fields. "
							+ line);
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			log.error("Unable to locate the file requested", e);
			throw new UnableToIndexSchoolsException("Unable to locate the file requested", e);
		} catch (IOException e) {
			throw new UnableToIndexSchoolsException("Unable to load the file requested", e);
		}

		return schools;
	}
}
