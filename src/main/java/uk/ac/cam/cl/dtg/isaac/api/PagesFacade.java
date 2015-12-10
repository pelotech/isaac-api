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
package uk.ac.cam.cl.dtg.isaac.api;

import static uk.ac.cam.cl.dtg.isaac.api.Constants.*;
import static uk.ac.cam.cl.dtg.segue.api.Constants.CONTENT_VERSION;
import static uk.ac.cam.cl.dtg.segue.api.Constants.DEFAULT_RESULTS_LIMIT;
import static uk.ac.cam.cl.dtg.segue.api.Constants.DEFAULT_RESULTS_LIMIT_AS_STRING;
import static uk.ac.cam.cl.dtg.segue.api.Constants.DEFAULT_START_INDEX_AS_STRING;
import static uk.ac.cam.cl.dtg.segue.api.Constants.ID_FIELDNAME;
import static uk.ac.cam.cl.dtg.segue.api.Constants.LEVEL_FIELDNAME;
import static uk.ac.cam.cl.dtg.segue.api.Constants.NEVER_CACHE_WITHOUT_ETAG_CHECK;
import static uk.ac.cam.cl.dtg.segue.api.Constants.TAGS_FIELDNAME;
import static uk.ac.cam.cl.dtg.segue.api.Constants.TYPE_FIELDNAME;
import static uk.ac.cam.cl.dtg.segue.api.Constants.UNPROCESSED_SEARCH_FIELD_SUFFIX;
import static uk.ac.cam.cl.dtg.segue.api.Constants.NUMBER_SECONDS_IN_ONE_HOUR;
import static uk.ac.cam.cl.dtg.segue.api.Constants.NUMBER_SECONDS_IN_TEN_MINUTES;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import ma.glasnost.orika.MapperFacade;

import org.jboss.resteasy.annotations.GZIP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.isaac.api.managers.URIManager;
import uk.ac.cam.cl.dtg.isaac.dto.IsaacQuestionPageDTO;
import uk.ac.cam.cl.dtg.segue.api.SegueApiFacade;
import uk.ac.cam.cl.dtg.segue.api.managers.ContentVersionController;
import uk.ac.cam.cl.dtg.segue.api.managers.QuestionManager;
import uk.ac.cam.cl.dtg.segue.api.managers.UserManager;
import uk.ac.cam.cl.dtg.segue.dao.ILogManager;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dao.content.ContentManagerException;
import uk.ac.cam.cl.dtg.segue.dos.QuestionValidationResponse;
import uk.ac.cam.cl.dtg.segue.dto.ResultsWrapper;
import uk.ac.cam.cl.dtg.segue.dto.SegueErrorResponse;
import uk.ac.cam.cl.dtg.segue.dto.content.ContentDTO;
import uk.ac.cam.cl.dtg.segue.dto.content.ContentSummaryDTO;
import uk.ac.cam.cl.dtg.segue.dto.content.SeguePageDTO;
import uk.ac.cam.cl.dtg.segue.dto.users.AbstractSegueUserDTO;
import uk.ac.cam.cl.dtg.segue.dto.users.AnonymousUserDTO;
import uk.ac.cam.cl.dtg.segue.dto.users.RegisteredUserDTO;
import uk.ac.cam.cl.dtg.util.PropertiesLoader;

import com.google.api.client.util.Maps;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

/**
 * Pages Facade
 * 
 * This class specifically caters for displaying isaac specific content pages.
 */
@Path("/pages")
@Api(value = "/pages")
public class PagesFacade extends AbstractIsaacFacade {
    private static final Logger log = LoggerFactory.getLogger(PagesFacade.class);

    private final SegueApiFacade api;
    private final MapperFacade mapper;
    private final ContentVersionController versionManager;
    private final UserManager userManager;
    private final URIManager uriManager;
    private final QuestionManager questionManager;

    /**
     * Creates an instance of the pages controller which provides the REST endpoints for accessing page content.
     * 
     * @param api
     *            - Instance of segue Api
     * @param propertiesLoader
     *            - Instance of properties Loader
     * @param logManager
     *            - Instance of Log Manager
     * @param mapper
     *            - Instance of Mapper facade.
     * @param versionManager
     *            - so we can find out the latest content version.
     * @param userManager
     *            - So we can interrogate the user Manager.
     * @param uriManager
     *            - URI manager so we can augment uris
     * @param questionManager
     *            - So we can look up attempt information.
     */
    @Inject
    public PagesFacade(final SegueApiFacade api, final PropertiesLoader propertiesLoader,
            final ILogManager logManager, final MapperFacade mapper, final ContentVersionController versionManager,
            final UserManager userManager, final URIManager uriManager, final QuestionManager questionManager) {
        super(propertiesLoader, logManager);
        this.api = api;
        this.mapper = mapper;
        this.versionManager = versionManager;
        this.userManager = userManager;
        this.uriManager = uriManager;
        this.questionManager = questionManager;
    }

    /**
     * REST end point to provide a list of concepts.
     * 
     * Uses ETag caching to attempt to reduce load on the server.
     *
     * @param request
     *            - used to determine if we can return a cache response.
     * @param ids
     *            - the ids of the concepts to request.
     * @param tags
     *            - a comma separated list of strings
     * @param startIndex
     *            - a string value to be converted into an integer which represents the start index of the results
     * @param limit
     *            - a string value to be converted into an integer that represents the number of results to return.
     * @return A response object which contains a list of concepts or an empty list.
     */
    @GET
    @Path("/concepts")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    @ApiOperation(value = "Gets the list of concept pages")
    public final Response getConceptList(@Context final Request request, @QueryParam("ids") final String ids,
            @QueryParam("tags") final String tags,
            @DefaultValue(DEFAULT_START_INDEX_AS_STRING) @QueryParam("start_index") final Integer startIndex,
            @DefaultValue(DEFAULT_RESULTS_LIMIT_AS_STRING) @QueryParam("limit") final Integer limit) {
        Map<String, List<String>> fieldsToMatch = Maps.newHashMap();
        fieldsToMatch.put(TYPE_FIELDNAME, Arrays.asList(CONCEPT_TYPE));

        StringBuilder etagCodeBuilder = new StringBuilder();

        Integer newLimit = null;

        if (limit != null) {
            newLimit = limit;
            etagCodeBuilder.append(limit.toString());
        }

        // options
        if (ids != null) {
            List<String> idsList = Arrays.asList(ids.split(","));
            fieldsToMatch.put(ID_FIELDNAME, idsList);
            newLimit = idsList.size();
            etagCodeBuilder.append(ids);
        }

        if (tags != null) {
            fieldsToMatch.put(TAGS_FIELDNAME, Arrays.asList(tags.split(",")));
            etagCodeBuilder.append(tags);
        }

        // Calculate the ETag on last modified date of tags list
        // NOTE: Assumes that the latest version of the content is being used.
        EntityTag etag = new EntityTag(versionManager.getLiveVersion().hashCode()
                + etagCodeBuilder.toString().hashCode() + "");

        Response cachedResponse = generateCachedResponse(request, etag);

        if (cachedResponse != null) {
            return cachedResponse;
        }

        return listContentObjects(fieldsToMatch, startIndex, newLimit).tag(etag)
                .cacheControl(getCacheControl(NUMBER_SECONDS_IN_ONE_HOUR, true))
                .build();
    }

    /**
     * Rest end point that gets a single concept based on a given id.
     * 
     * @param request
     *            - so we can deal with caching and ETags.
     * @param servletRequest
     *            - so we can extract user information for logging.
     * @param conceptId
     *            as a string
     * @return A Response object containing a concept object.
     */
    @GET
    @Path("/concepts/{concept_page_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    public final Response getConcept(@Context final Request request, @Context final HttpServletRequest servletRequest,
            @PathParam("concept_page_id") final String conceptId) {
        if (null == conceptId || conceptId.isEmpty()) {
            return new SegueErrorResponse(Status.BAD_REQUEST, "You must provide a valid concept id.").toResponse();
        }

        // Calculate the ETag on current live version of the content
        // NOTE: Assumes that the latest version of the content is being used.
        EntityTag etag = new EntityTag(versionManager.getLiveVersion().hashCode() + "byId".hashCode()
                + conceptId.hashCode() + "");
        Response cachedResponse = generateCachedResponse(request, etag);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        Map<String, List<String>> fieldsToMatch = Maps.newHashMap();
        fieldsToMatch.put(TYPE_FIELDNAME, Arrays.asList(CONCEPT_TYPE));

        // options
        if (null != conceptId) {
            fieldsToMatch.put(ID_FIELDNAME + "." + UNPROCESSED_SEARCH_FIELD_SUFFIX, Arrays.asList(conceptId));
        }

        Response result = this.findSingleResult(fieldsToMatch);

        if (result.getEntity() instanceof SeguePageDTO) {
            ImmutableMap<String, String> logEntry = new ImmutableMap.Builder<String, String>()
                    .put(CONCEPT_ID_LOG_FIELDNAME, conceptId).put(CONTENT_VERSION, versionManager.getLiveVersion())
                    .build();

            // the request log
            getLogManager().logEvent(userManager.getCurrentUser(servletRequest), servletRequest,
                    Constants.VIEW_CONCEPT, logEntry);
        }

        Response cachableResult = Response.status(result.getStatus()).entity(result.getEntity())
                .cacheControl(getCacheControl(NUMBER_SECONDS_IN_ONE_HOUR, true)).tag(etag).build();

        return cachableResult;
    }

    /**
     * REST end point to provide a list of questions.
     * 
     * @param request
     *            - used to determine if we can return a cache response.
     * @param ids
     *            - the ids of the concepts to request.
     * @param searchString
     *            - an optional search string to allow finding of questions by title.
     * @param tags
     *            - a comma separated list of strings
     * @param level
     *            - a string value to be converted into an integer which represents the levels that must match the
     *            questions returned.
     * @param startIndex
     *            - a string value to be converted into an integer which represents the start index of the results
     * @param limit
     *            - a string value to be converted into an integer that represents the number of results to return.
     * @return A response object which contains a list of questions or an empty list.
     */
    @GET
    @Path("/questions")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    public final Response getQuestionList(@Context final Request request, @QueryParam("ids") final String ids,
            @QueryParam("searchString") final String searchString, @QueryParam("tags") final String tags,
            @QueryParam("levels") final String level,
            @DefaultValue(DEFAULT_START_INDEX_AS_STRING) @QueryParam("start_index") final Integer startIndex,
            @DefaultValue(DEFAULT_RESULTS_LIMIT_AS_STRING) @QueryParam("limit") final Integer limit) {
        StringBuilder etagCodeBuilder = new StringBuilder();
        Map<String, List<String>> fieldsToMatch = Maps.newHashMap();

        fieldsToMatch.put(TYPE_FIELDNAME, Arrays.asList(QUESTION_TYPE));
        etagCodeBuilder.append(QUESTION_TYPE);

        // defaults
        int newLimit = DEFAULT_RESULTS_LIMIT;
        int newStartIndex = 0;

        // options
        if (limit != null) {
            newLimit = limit;
        }

        if (startIndex != null) {
            newStartIndex = startIndex;
        }

        if (ids != null && !ids.isEmpty()) {
            List<String> idsList = Arrays.asList(ids.split(","));
            fieldsToMatch.put(ID_FIELDNAME, idsList);
            newLimit = idsList.size();
            etagCodeBuilder.append(ids);
        }

        if (tags != null && !tags.isEmpty()) {
            fieldsToMatch.put(TAGS_FIELDNAME, Arrays.asList(tags.split(",")));
            etagCodeBuilder.append(tags);
        }

        if (level != null && !level.isEmpty()) {
            fieldsToMatch.put(LEVEL_FIELDNAME, Arrays.asList(level.split(",")));
            etagCodeBuilder.append(level);
        }

        // Calculate the ETag on last modified date of tags list
        // NOTE: Assumes that the latest version of the content is being used.
        EntityTag etag = new EntityTag(versionManager.getLiveVersion().hashCode()
                + etagCodeBuilder.toString().hashCode() + "");

        Response cachedResponse = generateCachedResponse(request, etag);

        if (cachedResponse != null) {
            return cachedResponse;
        }

        // Currently if you provide a search string we use a different
        // library call. This is because the previous one does not allow fuzzy
        // search.
        if (searchString != null && !searchString.isEmpty()) {
            ResultsWrapper<ContentDTO> c;
            try {
                c = api.segueSearch(searchString, versionManager.getLiveVersion(), fieldsToMatch, newStartIndex,
                        newLimit);
            } catch (ContentManagerException e1) {
                SegueErrorResponse error = new SegueErrorResponse(Status.NOT_FOUND,
                        "Error locating the version requested", e1);
                log.error(error.getErrorMessage(), e1);
                return error.toResponse();
            }

            ResultsWrapper<ContentSummaryDTO> summarizedContent = new ResultsWrapper<ContentSummaryDTO>(
                    this.extractContentSummaryFromList(c.getResults()),
                    c.getTotalResults());

            return Response.ok(summarizedContent).tag(etag)
                    .cacheControl(getCacheControl(NUMBER_SECONDS_IN_ONE_HOUR, true))
                    .build();
        } else {
            return listContentObjects(fieldsToMatch, newStartIndex, newLimit).tag(etag)
                    .cacheControl(getCacheControl(NUMBER_SECONDS_IN_ONE_HOUR, true)).build();
        }
    }

    /**
     * Rest end point that gets a single question page based on a given id.
     * 
     * @param questionId
     *            to find as a string
     * @param request
     *            - so that we can do etag and cache resolution.
     * @param httpServletRequest
     *            - so that we can try and determine if the user is logged in. This will allow us to augment the
     *            question objects with any recorded state.
     * @return A Response object containing a question page object or a SegueErrorResponse.
     */
    @GET
    @Path("/questions/{question_page_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    public final Response getQuestion(@Context final Request request,
            @Context final HttpServletRequest httpServletRequest, 
            @PathParam("question_page_id") final String questionId) {
        Map<String, List<String>> fieldsToMatch = Maps.newHashMap();

        fieldsToMatch.put("type", Arrays.asList(QUESTION_TYPE, FAST_TRACK_QUESTION_TYPE));

        // options
        if (null != questionId) {
            fieldsToMatch.put(ID_FIELDNAME + "." + UNPROCESSED_SEARCH_FIELD_SUFFIX, Arrays.asList(questionId));
        }
        AbstractSegueUserDTO user = userManager.getCurrentUser(httpServletRequest);
        Map<String, Map<String, List<QuestionValidationResponse>>> userQuestionAttempts;

        try {
            userQuestionAttempts = questionManager.getQuestionAttemptsByUser(user);
        } catch (SegueDatabaseException e) {
            String message = "SegueDatabaseException whilst trying to retrieve user question data";
            log.error(message, e);
            return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, message).toResponse();
        }

        // Calculate the ETag
        EntityTag etag = new EntityTag(questionId.hashCode() + userQuestionAttempts.toString().hashCode() + "");

        Response cachedResponse = generateCachedResponse(request, etag, NEVER_CACHE_WITHOUT_ETAG_CHECK);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        Response response = this.findSingleResult(fieldsToMatch);

        if (response.getEntity() != null && response.getEntity() instanceof IsaacQuestionPageDTO) {
            SeguePageDTO content = (SeguePageDTO) response.getEntity();

            Map<String, String> logEntry = ImmutableMap.of(QUESTION_ID_LOG_FIELDNAME, content.getId(),
                    "contentVersion", versionManager.getLiveVersion());

            String userIdForRandomisation;
            if (user instanceof AnonymousUserDTO) {
                userIdForRandomisation = ((AnonymousUserDTO) user).getSessionId();
            } else {
                userIdForRandomisation = ((RegisteredUserDTO) user).getId().toString();
            }

            content = api.getQuestionManager().augmentQuestionObjects(content, userIdForRandomisation,
                    userQuestionAttempts);

            // the request log
            getLogManager().logEvent(user, httpServletRequest, Constants.VIEW_QUESTION, logEntry);

            // return augmented content.
            return Response.ok(content)
                    .cacheControl(getCacheControl(NEVER_CACHE_WITHOUT_ETAG_CHECK, false))
                    .tag(etag)
                    .build();
        } else {
            String error = "Unable to locate a question with the id specified: " + questionId;
            log.warn(error);
            return SegueErrorResponse.getResourceNotFoundResponse(error);
        }
    }

    /**
     * Rest end point that gets a single page based on a given id.
     * 
     * @param request
     *            - so we can deal with caching.
     * @param httpServletRequest
     *            - so that we can extract user information.
     * @param pageId
     *            as a string
     * @return A Response object containing a page object or containing a SegueErrorResponse.
     */
    @GET
    @Path("/{page}")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    public final Response getPage(@Context final Request request, @Context final HttpServletRequest httpServletRequest,
            @PathParam("page") final String pageId) {
        // Calculate the ETag on current live version of the content
        // NOTE: Assumes that the latest version of the content is being used.
        EntityTag etag = new EntityTag(versionManager.getLiveVersion().hashCode() + pageId.hashCode() + "");

        Response cachedResponse = generateCachedResponse(request, etag);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        Map<String, List<String>> fieldsToMatch = Maps.newHashMap();
        fieldsToMatch.put(TYPE_FIELDNAME, Arrays.asList(PAGE_TYPE, QUESTIONS_PAGE_TYPE));

        // options
        if (null != pageId) {
            fieldsToMatch.put(ID_FIELDNAME + "." + UNPROCESSED_SEARCH_FIELD_SUFFIX, Arrays.asList(pageId));
        }

        Response result = this.findSingleResult(fieldsToMatch);

        if (result.getEntity() instanceof SeguePageDTO) {
            ImmutableMap<String, String> logEntry = new ImmutableMap.Builder<String, String>()
                    .put(PAGE_ID_LOG_FIELDNAME, pageId).put(CONTENT_VERSION, versionManager.getLiveVersion()).build();

            // the request log
            getLogManager().logEvent(userManager.getCurrentUser(httpServletRequest), httpServletRequest,
                    Constants.VIEW_PAGE, logEntry);
        }

        Response cachableResult = Response.status(result.getStatus()).entity(result.getEntity())
                .cacheControl(getCacheControl(NUMBER_SECONDS_IN_ONE_HOUR, true)).tag(etag).build();
        return cachableResult;
    }

    /**
     * Rest end point that gets a single page fragment based on a given id.
     * 
     * @param request
     *            - so that we can deal with caching.
     * @param fragmentId
     *            as a string
     * @return A Response object containing a page fragment object or containing a SegueErrorResponse.
     */
    @GET
    @Path("/fragments/{fragment_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    public final Response getPageFragment(@Context final Request request,
            @PathParam("fragment_id") final String fragmentId) {

        // Calculate the ETag on current live version of the content
        // NOTE: Assumes that the latest version of the content is being used.
        EntityTag etag = new EntityTag(versionManager.getLiveVersion().hashCode() + fragmentId.hashCode() + "");
        Response cachedResponse = generateCachedResponse(request, etag);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        Map<String, List<String>> fieldsToMatch = Maps.newHashMap();
        fieldsToMatch.put(TYPE_FIELDNAME, Arrays.asList(PAGE_FRAGMENT_TYPE));

        // options
        if (null != fragmentId) {
            fieldsToMatch.put(ID_FIELDNAME + "." + UNPROCESSED_SEARCH_FIELD_SUFFIX, Arrays.asList(fragmentId));
        }
        Response result = this.findSingleResult(fieldsToMatch);

        Response cachableResult = Response.status(result.getStatus()).entity(result.getEntity())
                .cacheControl(getCacheControl(NUMBER_SECONDS_IN_ONE_HOUR, true)).tag(etag).build();

        return cachableResult;
    }

    /**
     * Rest end point that gets a all of the content marked as being type "pods".
     * 
     * @param request
     *            - so that we can deal with caching.
     * @return A Response object containing a page fragment object or containing a SegueErrorResponse.
     */
    @GET
    @Path("/pods")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    public final Response getPodList(@Context final Request request) {

        // Calculate the ETag on current live version of the content
        // NOTE: Assumes that the latest version of the content is being used.
        EntityTag etag = new EntityTag(versionManager.getLiveVersion().hashCode() + "");
        Response cachedResponse = generateCachedResponse(request, etag);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        Map<String, List<String>> fieldsToMatch = Maps.newHashMap();
        fieldsToMatch.put(TYPE_FIELDNAME, Arrays.asList(POD_FRAGMENT_TYPE));

        ResultsWrapper<ContentDTO> pods = api.findMatchingContent(versionManager.getLiveVersion(),
                SegueApiFacade.generateDefaultFieldToMatch(fieldsToMatch), 0, MAX_PODS_TO_RETURN);

        Response cachableResult = Response.ok(pods).cacheControl(getCacheControl(NUMBER_SECONDS_IN_TEN_MINUTES, true))
                .tag(etag)
                .build();

        return cachableResult;
    }

    /**
     * This method will extract basic information from a content object so the lighter ContentInfo object can be sent to
     * the client instead.
     * 
     * @param content
     *            - the content object to summarise
     * @return ContentSummaryDTO.
     */
    private ContentSummaryDTO extractContentSummary(final ContentDTO content) {
        if (null == content) {
            return null;
        }

        // try auto-mapping
        ContentSummaryDTO contentInfo = mapper.map(content, ContentSummaryDTO.class);
        contentInfo.setUrl(uriManager.generateApiUrl(content));

        return contentInfo;
    }

    /**
     * Utility method to convert a list of content objects into a list of ContentSummaryDTO Objects.
     * 
     * @param contentList
     *            - the list of content to summarise.
     * @return list of shorter ContentSummaryDTO objects.
     */
    private List<ContentSummaryDTO> extractContentSummaryFromList(final List<ContentDTO> contentList) {
        if (null == contentList) {
            return null;
        }

        List<ContentSummaryDTO> listOfContentInfo = new ArrayList<ContentSummaryDTO>();

        for (ContentDTO content : contentList) {
            ContentSummaryDTO contentInfo = extractContentSummary(content);
            if (null != contentInfo) {
                listOfContentInfo.add(contentInfo);
            }
        }
        return listOfContentInfo;
    }

    /**
     * For use when we expect to only find a single result.
     * 
     * By default related content ContentSummary objects will be fully augmented.
     * 
     * @param fieldsToMatch
     *            - expects a map of the form fieldname -> list of queries to match
     * @return A Response containing a single conceptPage or containing a SegueErrorResponse.
     */
    private Response findSingleResult(final Map<String, List<String>> fieldsToMatch) {
        ResultsWrapper<ContentDTO> resultList = api.findMatchingContent(versionManager.getLiveVersion(),
                SegueApiFacade.generateDefaultFieldToMatch(fieldsToMatch), null, null); // includes
                                                                                        // type
                                                                                        // checking.
        ContentDTO c = null;
        if (resultList.getResults().size() > 1) {
            return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Multiple results ("
                    + resultList.getResults().size() + ") returned error. For search query: " + fieldsToMatch.values())
                    .toResponse();
        } else if (resultList.getResults().isEmpty()) {
            return new SegueErrorResponse(Status.NOT_FOUND, "No content found that matches the query with parameters: "
                    + fieldsToMatch.values()).toResponse();
        } else {
            c = resultList.getResults().get(0);
        }

        try {
            return Response.ok(api.augmentContentWithRelatedContent(versionManager.getLiveVersion(), c)).build();
        } catch (ContentManagerException e1) {
            SegueErrorResponse error = new SegueErrorResponse(Status.NOT_FOUND, "Error locating the version requested",
                    e1);
            log.error(error.getErrorMessage(), e1);
            return error.toResponse();
        }
    }

    /**
     * Helper method to query segue for a list of content objects.
     * 
     * This method will only use the latest version of the content.
     * 
     * @param fieldsToMatch
     *            - expects a map of the form fieldname -> list of queries to match
     * @param startIndex
     *            - the initial index for the first result.
     * @param limit
     *            - the maximums number of results to return
     * @return Response builder containing a list of content summary objects or containing a SegueErrorResponse
     */
    private Response.ResponseBuilder listContentObjects(final Map<String, List<String>> fieldsToMatch,
            final Integer startIndex, final Integer limit) {
        ResultsWrapper<ContentDTO> c;

        c = api.findMatchingContent(versionManager.getLiveVersion(),
                SegueApiFacade.generateDefaultFieldToMatch(fieldsToMatch), startIndex, limit);

        ResultsWrapper<ContentSummaryDTO> summarizedContent = new ResultsWrapper<ContentSummaryDTO>(
                this.extractContentSummaryFromList(c.getResults()),
                c.getTotalResults());

        return Response.ok(summarizedContent);
    }
}
