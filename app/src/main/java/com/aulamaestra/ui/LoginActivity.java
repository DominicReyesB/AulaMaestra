package com.aulamaestra.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.db.SessionManager;
import com.aulamaestra.model.StudentJoinResult;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {
    private final AulaRepository repo = AulaRepository.get();
    private SessionManager session;
    private View teacherBox;
    private View studentBox;
    private View studentReturnBox;
    private View studentRegisterBox;
    private TextView textStudentWelcome;
    private TextInputEditText inputUser;
    private TextInputEditText inputPass;
    private TextInputEditText inputCode;
    private TextInputEditText inputCodeReturn;
    private TextInputEditText inputStudentName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        session = new SessionManager(this);

        teacherBox = findViewById(R.id.teacherBox);
        studentBox = findViewById(R.id.studentBox);
        studentReturnBox = findViewById(R.id.studentReturnBox);
        studentRegisterBox = findViewById(R.id.studentRegisterBox);
        textStudentWelcome = findViewById(R.id.textStudentWelcome);
        inputUser = findViewById(R.id.inputUser);
        inputPass = findViewById(R.id.inputPass);
        inputCode = findViewById(R.id.inputCode);
        inputCodeReturn = findViewById(R.id.inputCodeReturn);
        inputStudentName = findViewById(R.id.inputStudentName);

        MaterialButtonToggleGroup roleToggle = findViewById(R.id.roleToggle);
        roleToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnTeacher) {
                teacherBox.setVisibility(View.VISIBLE);
                studentBox.setVisibility(View.GONE);
            } else if (checkedId == R.id.btnStudent) {
                teacherBox.setVisibility(View.GONE);
                studentBox.setVisibility(View.VISIBLE);
                updateStudentUi();
            }
        });

        findViewById(R.id.btnLoginTeacher).setOnClickListener(v -> loginTeacher());
        findViewById(R.id.btnRegisterTeacher).setOnClickListener(v -> registerTeacher());
        findViewById(R.id.btnJoinStudent).setOnClickListener(v -> registerAsStudent());
        findViewById(R.id.btnStudentLogin).setOnClickListener(v -> loginAsStudent());
        findViewById(R.id.btnStudentLogout).setOnClickListener(v -> {
            session.clearStudent();
            updateStudentUi();
            Toast.makeText(this, "Puedes registrarte con otro nombre", Toast.LENGTH_SHORT).show();
        });

        updateStudentUi();
    }

    private void updateStudentUi() {
        if (session.hasStudentSession()) {
            studentReturnBox.setVisibility(View.VISIBLE);
            studentRegisterBox.setVisibility(View.GONE);
            textStudentWelcome.setText(getString(R.string.student_welcome_back, session.getStudentName()));
        } else {
            studentReturnBox.setVisibility(View.GONE);
            studentRegisterBox.setVisibility(View.VISIBLE);
        }
    }

    private void loginTeacher() {
        String u = textOf(inputUser);
        String p = textOf(inputPass);
        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "Completa usuario y contraseña", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.loginTeacher(u, p, new RepoCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                session.setTeacherId(id);
                startActivity(new Intent(LoginActivity.this, TeacherSalonsActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registerTeacher() {
        String u = textOf(inputUser);
        String p = textOf(inputPass);
        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "Elige usuario y contraseña", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.registerTeacher(u, p, new RepoCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                session.setTeacherId(id);
                Toast.makeText(LoginActivity.this, "Cuenta creada", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, TeacherSalonsActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registerAsStudent() {
        if (session.hasStudentSession()) {
            Toast.makeText(this, R.string.student_already_registered, Toast.LENGTH_LONG).show();
            return;
        }
        String code = textOf(inputCode);
        String name = textOf(inputStudentName);
        if (code.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Código y nombre son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }
        joinSalon(code, name, null);
    }

    private void loginAsStudent() {
        if (!session.hasStudentSession()) {
            Toast.makeText(this, R.string.student_not_registered, Toast.LENGTH_LONG).show();
            return;
        }
        String code = textOf(inputCodeReturn);
        if (code.isEmpty()) {
            Toast.makeText(this, "Escribe el código del salón", Toast.LENGTH_SHORT).show();
            return;
        }
        joinSalon(code, null, session.getStudentId());
    }

    private void joinSalon(String code, String name, Long studentId) {
        repo.joinSalon(code, name, studentId, new RepoCallback<StudentJoinResult>() {
            @Override
            public void onSuccess(StudentJoinResult result) {
                session.saveStudentSession(result.studentId, result.displayName, result.salonId);
                openStudentSalon(result.salonId, result.studentId);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openStudentSalon(long salonId, long studentId) {
        Intent i = new Intent(this, StudentSalonActivity.class);
        i.putExtra(StudentSalonActivity.EXTRA_SALON_ID, salonId);
        i.putExtra(StudentSalonActivity.EXTRA_STUDENT_ID, studentId);
        startActivity(i);
        finish();
    }

    private static String textOf(TextInputEditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }
}
