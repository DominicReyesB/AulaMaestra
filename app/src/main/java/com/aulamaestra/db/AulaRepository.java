package com.aulamaestra.db;

import android.os.Handler;
import android.os.Looper;

import com.aulamaestra.api.ApiClient;
import com.aulamaestra.api.AulaApiService;
import com.aulamaestra.api.dto.AuthLoginResponse;
import com.aulamaestra.api.dto.AuthRequest;
import com.aulamaestra.api.dto.StudentRegisterRequest;
import com.aulamaestra.api.dto.EnrollRequest;
import com.aulamaestra.api.dto.GradeRequest;
import com.aulamaestra.api.dto.IdResponse;
import com.aulamaestra.api.dto.MessageDto;
import com.aulamaestra.api.dto.MessageRequest;
import com.aulamaestra.api.dto.OkResponse;
import com.aulamaestra.api.dto.PostCreateRequest;
import com.aulamaestra.api.dto.PostDto;
import com.aulamaestra.api.dto.SalonDto;
import com.aulamaestra.api.dto.StudentDto;
import com.aulamaestra.api.dto.StudentJoinRequest;
import com.aulamaestra.api.dto.StudentJoinResponse;
import com.aulamaestra.api.dto.StudentNameRequest;
import com.aulamaestra.model.StudentJoinResult;
import com.aulamaestra.api.dto.SubmissionCreateRequest;
import com.aulamaestra.api.dto.SubmissionDto;
import com.aulamaestra.api.dto.UploadResponse;
import com.aulamaestra.model.ChatMessage;
import com.aulamaestra.model.Post;
import com.aulamaestra.model.Salon;
import com.aulamaestra.model.Student;
import com.aulamaestra.model.SubmissionRow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AulaRepository {
    private static AulaRepository instance;
    private final AulaApiService api = ApiClient.api();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static synchronized AulaRepository get() {
        if (instance == null) {
            instance = new AulaRepository();
        }
        return instance;
    }

    private <T> void enqueue(Call<T> call, RepoCallback<T> cb) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    main.post(() -> cb.onSuccess(response.body()));
                } else {
                    main.post(() -> cb.onError(errorMessage(response)));
                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                main.post(() -> cb.onError(t.getMessage() != null ? t.getMessage() : "Sin conexión al servidor"));
            }
        });
    }

    private void enqueueId(Call<IdResponse> call, RepoCallback<Long> cb) {
        enqueue(call, new RepoCallback<IdResponse>() {
            @Override
            public void onSuccess(IdResponse data) {
                cb.onSuccess(data.id);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    private void runOk(Call<OkResponse> call, RepoCallback<Void> cb) {
        call.enqueue(new Callback<OkResponse>() {
            @Override
            public void onResponse(Call<OkResponse> call, Response<OkResponse> response) {
                if (response.isSuccessful()) {
                    main.post(() -> cb.onSuccess(null));
                } else {
                    main.post(() -> cb.onError(errorMessage(response)));
                }
            }

            @Override
            public void onFailure(Call<OkResponse> call, Throwable t) {
                main.post(() -> cb.onError(t.getMessage() != null ? t.getMessage() : "Sin conexión al servidor"));
            }
        });
    }

    private void runId(Call<IdResponse> call, RepoCallback<Void> cb) {
        call.enqueue(new Callback<IdResponse>() {
            @Override
            public void onResponse(Call<IdResponse> call, Response<IdResponse> response) {
                if (response.isSuccessful()) {
                    main.post(() -> cb.onSuccess(null));
                } else {
                    main.post(() -> cb.onError(errorMessage(response)));
                }
            }

            @Override
            public void onFailure(Call<IdResponse> call, Throwable t) {
                main.post(() -> cb.onError(t.getMessage() != null ? t.getMessage() : "Sin conexión al servidor"));
            }
        });
    }

    private static String errorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                if (raw.contains("error")) {
                    int i = raw.indexOf("\"error\"");
                    if (i >= 0) {
                        int start = raw.indexOf(':', i) + 1;
                        int q1 = raw.indexOf('"', start + 1);
                        int q2 = raw.indexOf('"', q1 + 1);
                        if (q1 >= 0 && q2 > q1) {
                            return raw.substring(q1 + 1, q2);
                        }
                    }
                }
                return raw.length() > 120 ? "Error del servidor" : raw;
            }
        } catch (IOException ignored) {
        }
        return "Error " + response.code();
    }

    private static Salon mapSalon(SalonDto d) {
        return new Salon(d.id, d.teacherId, d.number, d.inviteCode);
    }

    private static Student mapStudent(StudentDto d) {
        return new Student(d.id, d.displayName);
    }

    private static Post mapPost(PostDto d) {
        return new Post(d.id, d.type, d.title, d.body, d.filePath, d.createdAt);
    }

    private static SubmissionRow mapSubmission(SubmissionDto d) {
        return new SubmissionRow(
                d.submissionId, d.postId, d.assignmentTitle, d.studentId, d.studentName,
                d.textAnswer, d.filePath, d.linkUrl, d.attachmentsJson, d.submittedAt,
                d.score, d.feedback);
    }

    private static ChatMessage mapMessage(MessageDto d) {
        return new ChatMessage(d.id, d.fromTeacher, d.body, d.createdAt);
    }

    public void registerTeacher(String username, String password, RepoCallback<Long> cb) {
        enqueueId(api.registerTeacher(new AuthRequest(username, password)), cb);
    }

    public void loginTeacher(String username, String password, RepoCallback<Long> cb) {
        enqueueId(api.loginTeacher(new AuthRequest(username, password)), cb);
    }

    public void login(String username, String password, RepoCallback<AuthLoginResponse> cb) {
        api.login(new AuthRequest(username, password)).enqueue(new Callback<AuthLoginResponse>() {
            @Override
            public void onResponse(Call<AuthLoginResponse> call, Response<AuthLoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    main.post(() -> cb.onSuccess(response.body()));
                    return;
                }
                if (response.code() == 404 || response.code() == 405) {
                    loginLegacy(username, password, cb);
                    return;
                }
                main.post(() -> cb.onError(errorMessage(response)));
            }

            @Override
            public void onFailure(Call<AuthLoginResponse> call, Throwable t) {
                loginLegacy(username, password, cb);
            }
        });
    }

    private void loginLegacy(String username, String password, RepoCallback<AuthLoginResponse> cb) {
        loginTeacher(username.toLowerCase(), password, new RepoCallback<Long>() {
            @Override
            public void onSuccess(Long teacherId) {
                AuthLoginResponse r = new AuthLoginResponse();
                r.role = "teacher";
                r.teacherId = teacherId;
                main.post(() -> cb.onSuccess(r));
            }

            @Override
            public void onError(String teacherErr) {
                main.post(() -> cb.onError(
                        "Nombre o contraseña incorrectos. Si eres alumno nuevo, regístrate primero."));
            }
        });
    }

    public void registerStudent(String displayName, String password, String inviteCode,
                                RepoCallback<StudentJoinResponse> cb) {
        api.registerStudent(new StudentRegisterRequest(displayName, password, inviteCode))
                .enqueue(new Callback<StudentJoinResponse>() {
                    @Override
                    public void onResponse(Call<StudentJoinResponse> call, Response<StudentJoinResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            main.post(() -> cb.onSuccess(response.body()));
                            return;
                        }
                        if (response.code() == 404 || response.code() == 405) {
                            joinSalon(inviteCode, displayName, null, "register",
                                    new RepoCallback<StudentJoinResult>() {
                                        @Override
                                        public void onSuccess(StudentJoinResult result) {
                                            StudentJoinResponse r = new StudentJoinResponse();
                                            r.studentId = result.studentId;
                                            r.salonId = result.salonId;
                                            r.displayName = result.displayName;
                                            r.salonNumber = result.salonNumber;
                                            cb.onSuccess(r);
                                        }

                                        @Override
                                        public void onError(String message) {
                                            cb.onError(message);
                                        }
                                    });
                            return;
                        }
                        main.post(() -> cb.onError(errorMessage(response)));
                    }

                    @Override
                    public void onFailure(Call<StudentJoinResponse> call, Throwable t) {
                        joinSalon(inviteCode, displayName, null, "register",
                                new RepoCallback<StudentJoinResult>() {
                                    @Override
                                    public void onSuccess(StudentJoinResult result) {
                                        StudentJoinResponse r = new StudentJoinResponse();
                                        r.studentId = result.studentId;
                                        r.salonId = result.salonId;
                                        r.displayName = result.displayName;
                                        r.salonNumber = result.salonNumber;
                                        cb.onSuccess(r);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        cb.onError(message != null ? message : "Sin conexión al servidor");
                                    }
                                });
                    }
                });
    }

    public void listSalonsForTeacher(long teacherId, RepoCallback<List<Salon>> cb) {
        enqueue(api.listSalons(teacherId), new RepoCallback<List<SalonDto>>() {
            @Override
            public void onSuccess(List<SalonDto> list) {
                List<Salon> out = new ArrayList<>();
                for (SalonDto d : list) {
                    out.add(mapSalon(d));
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void findSalonById(long salonId, RepoCallback<Salon> cb) {
        enqueue(api.getSalon(salonId), new RepoCallback<SalonDto>() {
            @Override
            public void onSuccess(SalonDto d) {
                cb.onSuccess(mapSalon(d));
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void findSalonByCode(String code, RepoCallback<Salon> cb) {
        enqueue(api.getSalonByCode(code), new RepoCallback<SalonDto>() {
            @Override
            public void onSuccess(SalonDto d) {
                cb.onSuccess(mapSalon(d));
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void createStudent(String name, RepoCallback<Long> cb) {
        enqueueId(api.createStudent(new StudentNameRequest(name)), cb);
    }

    public void joinSalon(String inviteCode, String displayName, Long existingStudentId, String mode,
                          RepoCallback<StudentJoinResult> cb) {
        enqueue(api.joinSalon(new StudentJoinRequest(inviteCode, displayName, existingStudentId, mode)),
                new RepoCallback<StudentJoinResponse>() {
                    @Override
                    public void onSuccess(StudentJoinResponse r) {
                        cb.onSuccess(new StudentJoinResult(
                                r.studentId, r.salonId, r.displayName, r.salonNumber));
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    public void updateStudentName(long studentId, String name, RepoCallback<Void> cb) {
        runOk(api.updateStudent(studentId, new StudentNameRequest(name)), cb);
    }

    public void enrollStudent(long studentId, long salonId, RepoCallback<Void> cb) {
        runOk(api.enroll(salonId, new EnrollRequest(studentId)), cb);
    }

    public void listStudentsInSalon(long salonId, RepoCallback<List<Student>> cb) {
        enqueue(api.listStudents(salonId), new RepoCallback<List<StudentDto>>() {
            @Override
            public void onSuccess(List<StudentDto> list) {
                List<Student> out = new ArrayList<>();
                for (StudentDto d : list) {
                    out.add(mapStudent(d));
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void insertPost(long salonId, int postType, String title, String body, String filePath,
                           RepoCallback<Long> cb) {
        enqueueId(api.createPost(salonId, new PostCreateRequest(postType, title, body, filePath)), cb);
    }

    public void listPosts(long salonId, Integer typeFilter, RepoCallback<List<Post>> cb) {
        enqueue(api.listPosts(salonId, typeFilter), new RepoCallback<List<PostDto>>() {
            @Override
            public void onSuccess(List<PostDto> list) {
                List<Post> out = new ArrayList<>();
                for (PostDto d : list) {
                    out.add(mapPost(d));
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void upsertSubmission(long postId, long studentId, String text, String filePath,
                                 String linkUrl, String attachmentsJson, RepoCallback<Void> cb) {
        runId(api.submit(postId, new SubmissionCreateRequest(
                studentId, text, filePath, linkUrl, attachmentsJson)), cb);
    }

    public void listSubmissionsForSalon(long salonId, RepoCallback<List<SubmissionRow>> cb) {
        enqueue(api.listSubmissionsForSalon(salonId), new RepoCallback<List<SubmissionDto>>() {
            @Override
            public void onSuccess(List<SubmissionDto> list) {
                List<SubmissionRow> out = new ArrayList<>();
                for (SubmissionDto d : list) {
                    out.add(mapSubmission(d));
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void listSubmissionsForStudent(long salonId, long studentId, RepoCallback<List<SubmissionRow>> cb) {
        enqueue(api.listSubmissionsForStudent(salonId, studentId), new RepoCallback<List<SubmissionDto>>() {
            @Override
            public void onSuccess(List<SubmissionDto> list) {
                List<SubmissionRow> out = new ArrayList<>();
                for (SubmissionDto d : list) {
                    out.add(mapSubmission(d));
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void saveGrade(long submissionId, double score, String feedback, RepoCallback<Void> cb) {
        runOk(api.grade(submissionId, new GradeRequest(score, feedback)), cb);
    }

    public void insertMessage(long salonId, long studentId, boolean fromTeacher, String body,
                              RepoCallback<Void> cb) {
        runId(api.sendMessage(salonId, studentId, new MessageRequest(fromTeacher, body)), cb);
    }

    public void listMessages(long salonId, long studentId, RepoCallback<List<ChatMessage>> cb) {
        enqueue(api.listMessages(salonId, studentId), new RepoCallback<List<MessageDto>>() {
            @Override
            public void onSuccess(List<MessageDto> list) {
                List<ChatMessage> out = new ArrayList<>();
                for (MessageDto d : list) {
                    out.add(mapMessage(d));
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void getStudent(long studentId, RepoCallback<Student> cb) {
        enqueue(api.getStudent(studentId), new RepoCallback<StudentDto>() {
            @Override
            public void onSuccess(StudentDto d) {
                cb.onSuccess(mapStudent(d));
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void uploadLocalFile(String localPath, RepoCallback<String> cb) {
        executor.execute(() -> {
            File file = new File(localPath);
            if (!file.exists()) {
                main.post(() -> cb.onError("Archivo no encontrado"));
                return;
            }
            MediaType octet = MediaType.parse("application/octet-stream");
            RequestBody body = RequestBody.create(octet, file);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);
            try {
                Response<UploadResponse> response = api.upload(part).execute();
                if (response.isSuccessful() && response.body() != null) {
                    main.post(() -> cb.onSuccess(response.body().path));
                } else {
                    main.post(() -> cb.onError(errorMessage(response)));
                }
            } catch (IOException e) {
                main.post(() -> cb.onError(e.getMessage()));
            }
        });
    }
}
