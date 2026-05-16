package com.aulamaestra.db;

import android.os.Handler;
import android.os.Looper;

import com.aulamaestra.api.ApiClient;
import com.aulamaestra.api.AulaApiService;
import com.aulamaestra.api.dto.AuthRequest;
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
import com.aulamaestra.api.dto.StudentNameRequest;
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
                d.textAnswer, d.filePath, d.submittedAt, d.score, d.feedback);
    }

    private static ChatMessage mapMessage(MessageDto d) {
        return new ChatMessage(d.id, d.fromTeacher, d.body, d.createdAt);
    }

    public void registerTeacher(String username, String password, RepoCallback<Long> cb) {
        enqueue(api.registerTeacher(new AuthRequest(username, password)), r -> cb.onSuccess(r.id));
    }

    public void loginTeacher(String username, String password, RepoCallback<Long> cb) {
        enqueue(api.loginTeacher(new AuthRequest(username, password)), r -> cb.onSuccess(r.id));
    }

    public void listSalonsForTeacher(long teacherId, RepoCallback<List<Salon>> cb) {
        enqueue(api.listSalons(teacherId), list -> {
            List<Salon> out = new ArrayList<>();
            for (SalonDto d : list) {
                out.add(mapSalon(d));
            }
            cb.onSuccess(out);
        });
    }

    public void findSalonById(long salonId, RepoCallback<Salon> cb) {
        enqueue(api.getSalon(salonId), d -> cb.onSuccess(mapSalon(d)));
    }

    public void findSalonByCode(String code, RepoCallback<Salon> cb) {
        enqueue(api.getSalonByCode(code), d -> cb.onSuccess(mapSalon(d)));
    }

    public void createStudent(String name, RepoCallback<Long> cb) {
        enqueue(api.createStudent(new StudentNameRequest(name)), r -> cb.onSuccess(r.id));
    }

    public void updateStudentName(long studentId, String name, RepoCallback<Void> cb) {
        runOk(api.updateStudent(studentId, new StudentNameRequest(name)), cb);
    }

    public void enrollStudent(long studentId, long salonId, RepoCallback<Void> cb) {
        runOk(api.enroll(salonId, new EnrollRequest(studentId)), cb);
    }

    public void listStudentsInSalon(long salonId, RepoCallback<List<Student>> cb) {
        enqueue(api.listStudents(salonId), list -> {
            List<Student> out = new ArrayList<>();
            for (StudentDto d : list) {
                out.add(mapStudent(d));
            }
            cb.onSuccess(out);
        });
    }

    public void insertPost(long salonId, int postType, String title, String body, String filePath,
                           RepoCallback<Long> cb) {
        enqueue(api.createPost(salonId, new PostCreateRequest(postType, title, body, filePath)),
                r -> cb.onSuccess(r.id));
    }

    public void listPosts(long salonId, Integer typeFilter, RepoCallback<List<Post>> cb) {
        enqueue(api.listPosts(salonId, typeFilter), list -> {
            List<Post> out = new ArrayList<>();
            for (PostDto d : list) {
                out.add(mapPost(d));
            }
            cb.onSuccess(out);
        });
    }

    public void upsertSubmission(long postId, long studentId, String text, String filePath,
                                 RepoCallback<Void> cb) {
        runId(api.submit(postId, new SubmissionCreateRequest(studentId, text, filePath)), cb);
    }

    public void listSubmissionsForSalon(long salonId, RepoCallback<List<SubmissionRow>> cb) {
        enqueue(api.listSubmissionsForSalon(salonId), list -> {
            List<SubmissionRow> out = new ArrayList<>();
            for (SubmissionDto d : list) {
                out.add(mapSubmission(d));
            }
            cb.onSuccess(out);
        });
    }

    public void listSubmissionsForStudent(long salonId, long studentId, RepoCallback<List<SubmissionRow>> cb) {
        enqueue(api.listSubmissionsForStudent(salonId, studentId), list -> {
            List<SubmissionRow> out = new ArrayList<>();
            for (SubmissionDto d : list) {
                out.add(mapSubmission(d));
            }
            cb.onSuccess(out);
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
        enqueue(api.listMessages(salonId, studentId), list -> {
            List<ChatMessage> out = new ArrayList<>();
            for (MessageDto d : list) {
                out.add(mapMessage(d));
            }
            cb.onSuccess(out);
        });
    }

    public void getStudent(long studentId, RepoCallback<Student> cb) {
        enqueue(api.getStudent(studentId), d -> cb.onSuccess(mapStudent(d)));
    }

    public void uploadLocalFile(String localPath, RepoCallback<String> cb) {
        executor.execute(() -> {
            File file = new File(localPath);
            if (!file.exists()) {
                main.post(() -> cb.onError("Archivo no encontrado"));
                return;
            }
            RequestBody body = RequestBody.create(file, MediaType.parse("application/octet-stream"));
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
