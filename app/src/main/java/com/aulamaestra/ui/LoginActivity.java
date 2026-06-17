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
    private static final String MODE_LOGIN = "login";
    private static final String MODE_REGISTER = "register";

    private final AulaRepository repo = AulaRepository.get();
    private SessionManager session;
    private View teacherBox;
    private View studentBox;
    private TextView textStudentWelcome;
    private View btnSwitchStudent;
    private TextInputEditText inputUser;
    private TextInputEditText inputPass;
    private TextInputEditText inputStudentCode;
    private TextInputEditText inputStudentName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        session = new SessionManager(this);

        teacherBox = findViewById(R.id.teacherBox);
        studentBox = findViewById(R.id.studentBox);
        textStudentWelcome = findViewById(R.id.textStudentWelcome);
        btnSwitchStudent = findViewById(R.id.btnSwitchStudent);
        inputUser = findViewById(R.id.inputUser);
        inputPass = findViewById(R.id.inputPass);
        inputStudentCode = findViewById(R.id.inputStudentCode);
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
        findViewById(R.id.btnEnterStudent).setOnClickListener(v -> loginStudent());
        findViewById(R.id.btnRegisterStudent).setOnClickListener(v -> registerStudent());
        btnSwitchStudent.setOnClickListener(v -> {
            session.clearStudent();
            inputStudentCode.setText("");
            inputStudentName.setText("");
            updateStudentUi();
        });

        updateStudentUi();
    }

    private void updateStudentUi() {
        boolean saved = session.hasStudentSession();
        if (saved) {
            textStudentWelcome.setVisibility(View.VISIBLE);
            textStudentWelcome.setText(getString(R.string.student_welcome_back, session.getStudentName()));
            btnSwitchStudent.setVisibility(View.VISIBLE);
            if (textOf(inputStudentName).isEmpty()) {
                inputStudentName.setText(session.getStudentName());
            }
        } else {
            textStudentWelcome.setVisibility(View.GONE);
            btnSwitchStudent.setVisibility(View.GONE);
        }
    }

    private void loginTeacher() {
        String u = textOf(inputUser);
        String p = textOf(inputPass);
        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, R.string.login_fill_fields, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, R.string.login_fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        repo.registerTeacher(u, p, new RepoCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                session.setTeacherId(id);
                Toast.makeText(LoginActivity.this, R.string.register_ok, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, TeacherSalonsActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loginStudent() {
        String code = textOf(inputStudentCode);
        String name = textOf(inputStudentName);
        if (code.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, R.string.student_fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        Long savedId = session.hasStudentSession() && name.equalsIgnoreCase(session.getStudentName())
                ? session.getStudentId()
                : null;
        joinSalon(code, name, savedId, MODE_LOGIN);
    }

    private void registerStudent() {
        String code = textOf(inputStudentCode);
        String name = textOf(inputStudentName);
        if (code.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, R.string.student_fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        joinSalon(code, name, null, MODE_REGISTER);
    }

    private void joinSalon(String code, String name, Long studentId, String mode) {
        joinSalon(code, name, studentId, mode, false);
    }

    private void joinSalon(String code, String name, Long studentId, String mode, boolean retried) {
        repo.joinSalon(code, name, studentId, mode, new RepoCallback<StudentJoinResult>() {
            @Override
            public void onSuccess(StudentJoinResult result) {
                session.saveStudentSession(result.studentId, result.displayName, result.salonId);
                openStudentSalon(result.salonId, result.studentId);
            }

            @Override
            public void onError(String message) {
                if (!retried && MODE_LOGIN.equals(mode)
                        && (message.contains("Ya existe") || message.contains("No estás registrado")
                        || message.contains("No hay ningún alumno"))) {
                    Toast.makeText(LoginActivity.this, R.string.student_try_register, Toast.LENGTH_LONG).show();
                    return;
                }
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
