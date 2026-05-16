package com.aulamaestra.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.SessionManager;
import com.aulamaestra.model.Salon;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {
    private final AulaRepository repo = AulaRepository.get();
    private SessionManager session;
    private View teacherBox;
    private View studentBox;
    private TextInputEditText inputUser;
    private TextInputEditText inputPass;
    private TextInputEditText inputCode;
    private TextInputEditText inputStudentName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        session = new SessionManager(this);

        teacherBox = findViewById(R.id.teacherBox);
        studentBox = findViewById(R.id.studentBox);
        inputUser = findViewById(R.id.inputUser);
        inputPass = findViewById(R.id.inputPass);
        inputCode = findViewById(R.id.inputCode);
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
            }
        });

        findViewById(R.id.btnLoginTeacher).setOnClickListener(v -> loginTeacher());
        findViewById(R.id.btnRegisterTeacher).setOnClickListener(v -> registerTeacher());
        findViewById(R.id.btnJoinStudent).setOnClickListener(v -> joinAsStudent());
    }

    private void loginTeacher() {
        String u = textOf(inputUser);
        String p = textOf(inputPass);
        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "Completa usuario y contraseña", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.loginTeacher(u, p, new com.aulamaestra.db.RepoCallback<Long>() {
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
        repo.registerTeacher(u, p, new com.aulamaestra.db.RepoCallback<Long>() {
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

    private void joinAsStudent() {
        String code = textOf(inputCode);
        String name = textOf(inputStudentName);
        if (code.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Código y nombre son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.findSalonByCode(code, new com.aulamaestra.db.RepoCallback<Salon>() {
            @Override
            public void onSuccess(Salon salon) {
                long existing = session.getStudentId();
                if (existing < 0) {
                    repo.createStudent(name, new com.aulamaestra.db.RepoCallback<Long>() {
                        @Override
                        public void onSuccess(Long studentId) {
                            session.setStudentId(studentId);
                            enrollAndOpen(salon, studentId);
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    repo.updateStudentName(existing, name, new com.aulamaestra.db.RepoCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            enrollAndOpen(salon, existing);
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void enrollAndOpen(Salon salon, long studentId) {
        repo.enrollStudent(studentId, salon.id, new com.aulamaestra.db.RepoCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Intent i = new Intent(LoginActivity.this, StudentSalonActivity.class);
                i.putExtra(StudentSalonActivity.EXTRA_SALON_ID, salon.id);
                i.putExtra(StudentSalonActivity.EXTRA_STUDENT_ID, studentId);
                startActivity(i);
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String textOf(TextInputEditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }
}
