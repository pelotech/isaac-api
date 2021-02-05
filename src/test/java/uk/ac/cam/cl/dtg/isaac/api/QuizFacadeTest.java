/*
 * Copyright 2021 Raspberry Pi Foundation
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

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.ac.cam.cl.dtg.isaac.api.managers.DueBeforeNowException;
import uk.ac.cam.cl.dtg.isaac.api.managers.DuplicateAssignmentException;
import uk.ac.cam.cl.dtg.isaac.api.managers.QuizAssignmentManager;
import uk.ac.cam.cl.dtg.isaac.api.managers.QuizAttemptManager;
import uk.ac.cam.cl.dtg.isaac.api.managers.QuizManager;
import uk.ac.cam.cl.dtg.isaac.api.managers.QuizQuestionManager;
import uk.ac.cam.cl.dtg.isaac.api.services.AssignmentService;
import uk.ac.cam.cl.dtg.isaac.dos.QuizFeedbackMode;
import uk.ac.cam.cl.dtg.isaac.dto.IsaacQuizDTO;
import uk.ac.cam.cl.dtg.isaac.dto.QuizAssignmentDTO;
import uk.ac.cam.cl.dtg.isaac.dto.QuizAttemptDTO;
import uk.ac.cam.cl.dtg.segue.api.managers.GroupManager;
import uk.ac.cam.cl.dtg.segue.api.managers.UserAccountManager;
import uk.ac.cam.cl.dtg.segue.dao.ILogManager;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dao.content.ContentManagerException;
import uk.ac.cam.cl.dtg.segue.dao.content.IContentManager;
import uk.ac.cam.cl.dtg.segue.dto.QuestionValidationResponseDTO;
import uk.ac.cam.cl.dtg.segue.dto.ResultsWrapper;
import uk.ac.cam.cl.dtg.segue.dto.SegueErrorResponse;
import uk.ac.cam.cl.dtg.segue.dto.content.ChoiceDTO;
import uk.ac.cam.cl.dtg.segue.dto.content.ContentSummaryDTO;
import uk.ac.cam.cl.dtg.segue.dto.users.RegisteredUserDTO;
import uk.ac.cam.cl.dtg.util.PropertiesLoader;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.junit.Assert.assertEquals;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.createNiceMock;
import static org.powermock.api.easymock.PowerMock.expectLastCall;
import static org.powermock.api.easymock.PowerMock.replay;

@RunWith(PowerMockRunner.class)
@PrepareForTest({UserAccountManager.class})
@PowerMockIgnore({ "javax.ws.*", "javax.management.*", "javax.script.*" })
public class QuizFacadeTest extends AbstractFacadeTest {

    private QuizFacade quizFacade;

    private Request requestForCaching;

    private AssignmentService assignmentService;
    private QuizAttemptManager quizAttemptManager;
    private QuizQuestionManager quizQuestionManager;
    private QuizAssignmentManager quizAssignmentManager;

    @Before
    public void setUp() throws ContentManagerException, SegueDatabaseException {
        assignmentService = createMock(AssignmentService.class);

        requestForCaching = createMock(Request.class);
        expect(requestForCaching.evaluatePreconditions((EntityTag) anyObject())).andStubReturn(null);

        PropertiesLoader properties = createMock(PropertiesLoader.class);
        ILogManager logManager = createNiceMock(ILogManager.class); // We don't care about logging.
        IContentManager contentManager = createMock(IContentManager.class);
        QuizManager quizManager = createMock(QuizManager.class);
        GroupManager groupManager = createMock(GroupManager.class);
        quizAssignmentManager = createMock(QuizAssignmentManager.class);
        quizAttemptManager = createMock(QuizAttemptManager.class);
        quizQuestionManager = createMock(QuizQuestionManager.class);

        quizFacade = new QuizFacade(properties, logManager, contentManager, quizManager, userManager,
            groupManager, quizAssignmentManager, assignmentService, quizAttemptManager, quizQuestionManager);

        expect(quizManager.getAvailableQuizzes(true, null, null)).andStubReturn(wrap(studentQuizSummary));
        expect(quizManager.getAvailableQuizzes(false, null, null)).andStubReturn(wrap(studentQuizSummary, teacherQuizSummary));
        expect(quizManager.findQuiz(studentQuiz.getId())).andStubReturn(studentQuiz);
        expect(quizManager.findQuiz(teacherQuiz.getId())).andStubReturn(teacherQuiz);
        expect(quizManager.findQuiz(otherQuiz.getId())).andStubReturn(otherQuiz);

        registerDefaultsFor(quizAssignmentManager, m -> {
            expect(m.getAssignedQuizzes(anyObject(RegisteredUserDTO.class))).andStubAnswer(() -> {
                Object[] arguments = getCurrentArguments();
                if (arguments[0] == student) {
                    return studentAssignments;
                } else {
                    return Collections.emptyList();
                }
            });

            expect(m.getById(anyLong())).andStubAnswer(() -> {
                Object[] arguments = getCurrentArguments();
                return studentAssignments.stream()
                    .filter(assignment -> assignment.getId() == arguments[0])
                    .findFirst()
                    .orElseThrow(() -> new SegueDatabaseException("No such assignment."));
            });

            expect(m.getActiveQuizAssignments(anyObject(), anyObject())).andStubAnswer(() -> {
                Object[] arguments = getCurrentArguments();
                if (arguments[1] != student) return Collections.emptyList();
                return Collections.singletonList(studentAssignment);
            });
        });

        expect(groupManager.getGroupById(anyLong())).andStubAnswer(() -> {
            Object[] arguments = getCurrentArguments();
            if (arguments[0] == studentGroup.getId()) {
                return studentGroup;
            } else {
                throw new SegueDatabaseException("No such group.");
            }
        });

        expect(groupManager.isUserInGroup(anyObject(), anyObject())).andStubAnswer(() -> {
            Object[] arguments = getCurrentArguments();
            if (arguments[0] == student && arguments[1] == studentGroup) {
                return true;
            } else {
                return false;
            }
        });

        registerDefaultsFor(quizAttemptManager, m -> {
            expect(m.getById(anyLong())).andStubAnswer(() -> {
                Object[] arguments = getCurrentArguments();
                return studentAttempts.stream()
                    .filter(assignment -> assignment.getId() == arguments[0])
                    .findFirst()
                    .orElseThrow(() -> new SegueDatabaseException("No such attempt."));
            });
        });

        String currentSHA = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
        expect(contentManager.getCurrentContentSHA()).andStubReturn(currentSHA);
        expect(contentManager.extractContentSummary(studentQuiz)).andStubReturn(studentQuizSummary);
        expect(contentManager.extractContentSummary(teacherQuiz)).andStubReturn(teacherQuizSummary);
        expect(contentManager.getContentDOById(currentSHA, questionDO.getId())).andStubReturn(questionDO);
        expect(contentManager.getContentDOById(currentSHA, studentQuizDO.getId())).andStubReturn(studentQuizDO);
        expect(contentManager.getContentDOById(currentSHA, questionPageQuestionDO.getId())).andStubReturn(questionPageQuestionDO);

        replay(requestForCaching, properties, logManager, contentManager, quizManager, groupManager, quizAssignmentManager, assignmentService, quizAttemptManager, quizQuestionManager);
    }

    @Test
    public void availableQuizzes() {
        forEndpoint(() -> quizFacade.getAvailableQuizzes(request),
            requiresLogin(),
            as(anyOf(student, otherStudent),
                check((response) ->
                    assertEquals(Collections.singletonList(studentQuizSummary), extractResults(response)))
            ),
            as(teacher,
                check((response) ->
                    assertEquals(ImmutableList.of(studentQuizSummary, teacherQuizSummary), extractResults(response)))
            )
        );
    }

    @Test
    public void getAssignedQuizzes() {
        forEndpoint(() -> quizFacade.getAssignedQuizzes(request),
            requiresLogin(),
            as(student,
                prepare(assignmentService, (s) -> {
                    s.augmentAssignerSummaries(studentAssignments);
                    expectLastCall().once();
                }),
                respondsWith(studentAssignments)),
            as(anyOf(teacher, otherStudent),
                prepare(assignmentService, (s) -> {
                    s.augmentAssignerSummaries(Collections.emptyList());
                    expectLastCall().once();
                }),
                respondsWith(Collections.emptyList())
            ));
    }

    @Test
    public void createQuizAssignment() {
        QuizAssignmentDTO newAssignment = new QuizAssignmentDTO(0xB8003111799L, otherQuiz.getId(), null, studentGroup.getId(), null, someFutureDate, QuizFeedbackMode.OVERALL_MARK);
        QuizAssignmentDTO assignmentRequest = new QuizAssignmentDTO(null, otherQuiz.getId(), null, studentGroup.getId(), null, someFutureDate, QuizFeedbackMode.OVERALL_MARK);
        forEndpoint((QuizAssignmentDTO assignment) -> () -> quizFacade.createQuizAssignment(request, assignment),
            with(assignmentRequest,
                requiresLogin(),
                as(studentsTeachersOrAdmin(),
                    prepare(quizAssignmentManager, m -> expect(m.createAssignment(assignmentRequest)).andReturn(newAssignment)),
                    respondsWith(newAssignment),
                    check(ignoreResponse -> assertEquals(currentUser().getId(), assignmentRequest.getOwnerUserId()))
                ),
                forbiddenForEveryoneElse()
            ),
            with(assignmentRequest,
                as(studentsTeachersOrAdmin(),
                    prepare(quizAssignmentManager, m -> expect(m.createAssignment(assignmentRequest)).andThrow(new DueBeforeNowException())),
                    failsWith(Status.BAD_REQUEST)
                )
            ),
            with(assignmentRequest,
                as(studentsTeachersOrAdmin(),
                    prepare(quizAssignmentManager, m -> expect(m.createAssignment(assignmentRequest)).andThrow(new DuplicateAssignmentException("Test"))),
                    failsWith(Status.BAD_REQUEST)
                )
            )
        );
    }

    @Test
    public void cancelQuizAssignment() {
        forEndpoint(() -> quizFacade.cancelQuizAssignment(request, studentAssignment.getId()),
            requiresLogin(),
            as(studentsTeachersOrAdmin(),
                prepare(quizAssignmentManager, m -> m.cancelAssignment(studentAssignment)),
                succeeds()
            ),
            forbiddenForEveryoneElse()
        );
    }

    @Test
    public void previewQuiz() {
        forEndpoint(() -> quizFacade.previewQuiz(requestForCaching, request, studentQuiz.getId()),
            requiresLogin(),
            as(student, failsWith(SegueErrorResponse.getIncorrectRoleResponse())),
            as(teacher, respondsWith(studentQuiz))
        );
    }

    @Test
    public void startQuizAttempt() {
        QuizAttemptDTO attempt = new QuizAttemptDTO();

        forEndpoint(
            (assignment) -> () -> quizFacade.startQuizAttempt(requestForCaching, request, assignment.getId()),
            with(studentAssignment,
                requiresLogin(),
                as(student,
                    prepare(quizAttemptManager, m -> expect(m.fetchOrCreate(studentAssignment, student)).andReturn(attempt)),
                    respondsWith(attempt)),
                forbiddenForEveryoneElse()
            ),
            with(overdueAssignment,
                as(student, failsWith(Status.FORBIDDEN))
            )
        );
    }

    @Test
    public void startFreeQuizAttempt() {
        QuizAttemptDTO attempt = new QuizAttemptDTO();

        forEndpoint((quiz) -> () -> quizFacade.startFreeQuizAttempt(requestForCaching, request, quiz.getId()),
            with(studentQuiz,
                requiresLogin(),
                as(otherStudent,
                    prepare(quizAttemptManager, m -> expect(m.fetchOrCreateFreeQuiz(studentQuiz, otherStudent)).andReturn(attempt)),
                    respondsWith(attempt)
                ),
                as(student,
                    failsWith(Status.FORBIDDEN)
                )
            ),
            with(teacherQuiz,
                as(anyOf(student, otherStudent),
                    failsWith(Status.FORBIDDEN)
                )
            )
        );
    }

    @Test
    public void getQuizAttempt() {
        IsaacQuizDTO augmentedQuiz = new IsaacQuizDTO();
        forEndpoint((attempt) -> () -> quizFacade.getQuizAttempt(request, attempt.getId()),
            with(studentAttempt,
                requiresLogin(),
                as(otherStudent,
                    failsWith(Status.FORBIDDEN)
                ),
                as(student,
                    prepare(quizQuestionManager, m ->
                        expect(m.augmentQuestionsForUser(studentQuiz, studentAttempt, student, false)).andReturn(augmentedQuiz)),
                    respondsWith(augmentedQuiz)
                )
            ),
            with(overdueAttempt,
                as(student,
                    failsWith(Status.FORBIDDEN)
                )
            ),
            with(completedAttempt,
                as(student,
                    failsWith(Status.FORBIDDEN)
                )
            )
        );
    }

    @Test
    public void completeQuizAttempt() {
        forEndpoint((attempt) -> () -> quizFacade.completeQuizAttempt(request, attempt.getId()),
            with(studentAttempt,
                requiresLogin(),
                as(student,
                    prepare(quizAttemptManager, m -> m.updateAttemptCompletionStatus(studentAttempt, true)),
                    succeeds()
                ),
                everyoneElse(
                    failsWith(Status.FORBIDDEN)
                )
            ),
            with(completedAttempt,
                as(everyone,
                    failsWith(Status.FORBIDDEN)
                )
            )
        );
    }

    @Test
    public void completeQuizAttemptMarkIncompleteByTeacher() {
        forEndpoint((attempt) -> () -> quizFacade.markIncompleteQuizAttempt(request, attempt.getId()),
            with(studentAttempt,
                requiresLogin(),
                as(everyone,
                    failsWith(Status.FORBIDDEN)
                )
            ),
            with(completedAttempt,
                as(studentsTeachersOrAdmin(),
                    prepare(quizAttemptManager, m -> m.updateAttemptCompletionStatus(completedAttempt, false)),
                    succeeds()
                ),
                everyoneElse(
                    failsWith(Status.FORBIDDEN)
                )
            )
        );
    }

    @Test
    public void answerQuestion() {
        String jsonAnswer = "jsonAnswer";
        ChoiceDTO choice = new ChoiceDTO();
        QuestionValidationResponseDTO validationResponse = new QuestionValidationResponseDTO();

        forEndpoint((attempt) -> () -> quizFacade.answerQuestion(request, attempt.getId(), question.getId(), jsonAnswer),
            with(studentAttempt,
                requiresLogin(),
                as(student,
                    prepare(quizQuestionManager, m -> {
                        expect(m.convertJsonAnswerToChoice(jsonAnswer)).andReturn(choice);
                        expect(m.validateAnswer(questionDO, choice)).andReturn(validationResponse);
                        //expect(m.augmentQuestionsForUser(studentQuiz, studentAttempt, student, false)).andReturn(quiz);
                        m.recordQuestionAttempt(studentAttempt, validationResponse);
                    }),
                    succeeds()
                ),
                forbiddenForEveryoneElse()
            ),
            with(completedAttempt,
                as(everyone,
                    failsWith(Status.FORBIDDEN)
                )
            ),
            with(overdueAttempt,
                as(everyone,
                    failsWith(Status.FORBIDDEN)
                )
            )
        );
    }

    @Test
    public void answerQuestionOnWrongQuiz() {
        String jsonAnswer = "jsonAnswer";

        forEndpoint(() -> quizFacade.answerQuestion(request, otherAttempt.getId(), question.getId(), jsonAnswer),
            as(student,
                failsWith(Status.BAD_REQUEST)
            )
        );
    }

    @Test
    public void answerQuestionOnNonQuiz() {
        String jsonAnswer = "jsonAnswer";

        forEndpoint(() -> quizFacade.answerQuestion(request, studentAttempt.getId(), questionPageQuestion.getId(), jsonAnswer),
            as(student,
                failsWith(Status.BAD_REQUEST)
            )
        );
    }

    @Test
    public void abandonQuizAttempt() {
        forEndpoint((attempt) -> () -> quizFacade.abandonQuizAttempt(request, attempt.getId()),
            with(studentAttempt,
                requiresLogin(),
                as(everyone,
                    failsWith(Status.FORBIDDEN)
                )
            ),
            with(ownAttempt,
                as(student,
                    prepare(quizAttemptManager, m -> m.deleteAttempt(ownAttempt)),
                    succeeds()
                ),
                forbiddenForEveryoneElse()
            ),
            with(ownCompletedAttempt,
                as(everyone,
                    failsWith(Status.FORBIDDEN)
                )
            )
        );
    }

    public <E extends Exception> Check<E> forbiddenForEveryoneElse() {
        return everyoneElse(
            failsWith(Status.FORBIDDEN)
        );
    }

    @SafeVarargs
    final private <T> ResultsWrapper<T> wrap(T... items) {
        return new ResultsWrapper<>(Arrays.asList(items), (long) items.length);
    }

    private List<ContentSummaryDTO> extractResults(Response availableQuizzes) {
        return ((ResultsWrapper<ContentSummaryDTO>) availableQuizzes.getEntity()).getResults();
    }
}
