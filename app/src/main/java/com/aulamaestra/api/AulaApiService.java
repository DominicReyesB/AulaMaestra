package com.aulamaestra.api;

import com.aulamaestra.api.dto.AuthLoginResponse;
import com.aulamaestra.api.dto.AuthRequest;
import com.aulamaestra.api.dto.StudentRegisterRequest;
import com.aulamaestra.api.dto.EnrollRequest;
import com.aulamaestra.api.dto.GradeRequest;
import com.aulamaestra.api.dto.HelpAnswerResponse;
import com.aulamaestra.api.dto.HelpQuestionRequest;
import com.aulamaestra.api.dto.HelpStatusResponse;
import com.aulamaestra.api.dto.IdResponse;
import com.aulamaestra.api.dto.MessageRequest;
import com.aulamaestra.api.dto.MessageDto;
import com.aulamaestra.api.dto.OkResponse;
import com.aulamaestra.api.dto.PostCreateRequest;
import com.aulamaestra.api.dto.PostDto;
import com.aulamaestra.api.dto.SalonDto;
import com.aulamaestra.api.dto.StudentDto;
import com.aulamaestra.api.dto.StudentJoinRequest;
import com.aulamaestra.api.dto.StudentJoinResponse;
import com.aulamaestra.api.dto.StudentNameRequest;
import com.aulamaestra.api.dto.SubmissionCreateRequest;
import com.aulamaestra.api.dto.SubmissionDto;
import com.aulamaestra.api.dto.UploadResponse;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AulaApiService {
    @GET("api/help/status")
    Call<HelpStatusResponse> helpStatus();

    @POST("api/help/ask")
    Call<HelpAnswerResponse> askHelp(@Body HelpQuestionRequest body);

    @POST("api/teachers/register")
    Call<IdResponse> registerTeacher(@Body AuthRequest body);

    @POST("api/teachers/login")
    Call<IdResponse> loginTeacher(@Body AuthRequest body);

    @POST("api/auth/login")
    Call<AuthLoginResponse> login(@Body AuthRequest body);

    @POST("api/students/register")
    Call<StudentJoinResponse> registerStudent(@Body StudentRegisterRequest body);

    @POST("api/students/login")
    Call<AuthLoginResponse> loginStudent(@Body AuthRequest body);

    @GET("api/teachers/{teacherId}/salons")
    Call<List<SalonDto>> listSalons(@Path("teacherId") long teacherId);

    @GET("api/salons/{salonId}")
    Call<SalonDto> getSalon(@Path("salonId") long salonId);

    @GET("api/salons/by-code/{code}")
    Call<SalonDto> getSalonByCode(@Path("code") String code);

    @POST("api/students")
    Call<IdResponse> createStudent(@Body StudentNameRequest body);

    @POST("api/students/join-salon")
    Call<StudentJoinResponse> joinSalon(@Body StudentJoinRequest body);

    @PUT("api/students/{studentId}")
    Call<OkResponse> updateStudent(@Path("studentId") long studentId, @Body StudentNameRequest body);

    @GET("api/students/{studentId}")
    Call<StudentDto> getStudent(@Path("studentId") long studentId);

    @POST("api/salons/{salonId}/enroll")
    Call<OkResponse> enroll(@Path("salonId") long salonId, @Body EnrollRequest body);

    @GET("api/salons/{salonId}/students")
    Call<List<StudentDto>> listStudents(@Path("salonId") long salonId);

    @DELETE("api/salons/{salonId}/students/{studentId}")
    Call<OkResponse> deleteStudent(
            @Path("salonId") long salonId, @Path("studentId") long studentId);

    @POST("api/salons/{salonId}/students/{studentId}/delete")
    Call<OkResponse> deleteStudentCompatible(
            @Path("salonId") long salonId, @Path("studentId") long studentId);

    @GET("api/salons/{salonId}/posts")
    Call<List<PostDto>> listPosts(@Path("salonId") long salonId, @Query("type") Integer type);

    @POST("api/salons/{salonId}/posts")
    Call<IdResponse> createPost(@Path("salonId") long salonId, @Body PostCreateRequest body);

    @DELETE("api/posts/{postId}")
    Call<OkResponse> deletePost(@Path("postId") long postId);

    @POST("api/posts/{postId}/delete")
    Call<OkResponse> deletePostCompatible(@Path("postId") long postId);

    @POST("api/posts/{postId}/submissions")
    Call<IdResponse> submit(@Path("postId") long postId, @Body SubmissionCreateRequest body);

    @DELETE("api/submissions/{submissionId}/students/{studentId}")
    Call<OkResponse> deleteSubmission(
            @Path("submissionId") long submissionId, @Path("studentId") long studentId);

    @POST("api/submissions/{submissionId}/students/{studentId}/delete")
    Call<OkResponse> deleteSubmissionCompatible(
            @Path("submissionId") long submissionId, @Path("studentId") long studentId);

    @GET("api/salons/{salonId}/submissions")
    Call<List<SubmissionDto>> listSubmissionsForSalon(@Path("salonId") long salonId);

    @GET("api/salons/{salonId}/students/{studentId}/submissions")
    Call<List<SubmissionDto>> listSubmissionsForStudent(
            @Path("salonId") long salonId, @Path("studentId") long studentId);

    @PUT("api/submissions/{submissionId}/grade")
    Call<OkResponse> grade(@Path("submissionId") long submissionId, @Body GradeRequest body);

    @GET("api/salons/{salonId}/students/{studentId}/messages")
    Call<List<MessageDto>> listMessages(
            @Path("salonId") long salonId, @Path("studentId") long studentId);

    @POST("api/salons/{salonId}/students/{studentId}/messages")
    Call<IdResponse> sendMessage(
            @Path("salonId") long salonId,
            @Path("studentId") long studentId,
            @Body MessageRequest body);

    @Multipart
    @POST("api/upload")
    Call<UploadResponse> upload(@Part MultipartBody.Part file);
}
