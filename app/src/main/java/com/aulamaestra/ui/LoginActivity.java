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
    private View studentReturnBox;
    private View studentLoginBox;
    private View studentRegisterBox;
    private TextView textStudentWelcome;
    private TextView textRegisterSection;
    private TextInputEditText inputUser;
    private TextInputEditText inputPass;
    private TextInputEditText inputCode;
    private TextInputEditText inputCodeReturn;
    private TextInputEditText inputLoginCode;
    private TextInputEditText inputLoginName;
    private TextInputEditText inputStudentName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        session = new SessionManager(this);

        teacherBox = findViewById(R.id.teacherBox);
        studentBox = findViewById(R.id.studentBox);
        studentReturnBox = findViewById(R.id.studentReturnBox);
        studentLoginBox = findViewById(R.id.studentLoginBox);
        studentRegisterBox = findViewById(R.id.studentRegisterBox);
        textStudentWelcome = findViewById(R.id.textStudentWelcome);
        textRegisterSection = findViewById(R.id.textRegisterSection);
        inputUser = findViewById(R.id.inputUser);
        inputPass = findViewById(R.id.inputPass);
        inputCode = findViewById(R.id.inputCode);
        inputCodeReturn = findViewById(R.id.inputCodeReturn);
        inputLoginCode = findViewById(R.id.inputLoginCode);
        inputLoginName = findViewById(R.id.inputLoginName);
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
        findViewById(R.id.btnStudentLogin).setOnClickListener(v -> loginQuickOnDevice());
        findViewById(R.id.btnStudentLoginByName).setOnClickListener(v -> loginByName());
        findViewById(R.id.btnStudentLogout).setOnClickListener(v -> {
            session.clearStudent();
            inputCodeReturn.setText("");
            inputLoginCode.setText("");
            inputLoginName.setText("");
            inputCode.setText("");
            inputStudentName.setText("");
            updateStudentUi();
            Toast.makeText(this, "Ahora puedes registrar a otro alumno o entrar con nombre", Toast.LENGTH_SHORT).show();
        });

        updateStudentUi();
    }

    private void updateStudentUi() {
        boolean saved = session.hasStudentSession();
        studentReturnBox.setVisibility(saved ? View.VISIBLE : View.GONE);
        studentLoginBox.setVisibility(View.VISIBLE);
        studentRegisterBox.setVisibility(View.VISIBLE);

        if (saved) {
            textStudentWelcome.setText(getString(R.string.student_welcome_back, session.getStudentName()));
            textRegisterSection.setText("Otro alumno nuevo en este celular");
        } else {
            textRegisterSection.setText(getString(R.string.student_register_section));
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

    /** Entrada rápida: ya hay perfil guardado en este celular. */
    private void loginQuickOnDevice() {
        if (!session.hasStudentSession()) {
            Toast.makeText(this, "Usa «Iniciar sesión» con tu nombre abajo", Toast.LENGTH_LONG).show();
            return;
        }
        String code = textOf(inputCodeReturn);
        if (code.isEmpty()) {
            Toast.makeText(this, "Escribe el código del salón", Toast.LENGTH_SHORT).show();
            return;
        }
        joinSalon(code, null, session.getStudentId(), MODE_LOGIN);
    }

    /** Volver a entrar: código + mismo nombre de la primera vez. */
    private void loginByName() {
        String code = textOf(inputLoginCode);
        String name = textOf(inputLoginName);
        if (code.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Código y nombre son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }
        // Si aún hay id guardado y el nombre coincide, el servidor lo resuelve más rápido.
        Long savedId = session.hasStudentSession()
                && name.equalsIgnoreCase(session.getStudentName())
                ? session.getStudentId()
                : null;
        joinSalon(code, name, savedId, MODE_LOGIN);
    }

    private void registerAsStudent() {
        String code = textOf(inputCode);
        String name = textOf(inputStudentName);
        if (code.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Código y nombre son obligatorios", Toast.LENGTH_SHORT).show();
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
                // Servidor antiguo: "login" falla pero el alumno ya existe → reintentar como registro.
                if (!retried && MODE_LOGIN.equals(mode) && studentId == null
                        && (message.contains("Ya existe") || message.contains("Iniciar sesión")
                        || message.contains("No estás registrado"))) {
                    joinSalon(code, name, null, MODE_REGISTER, true);
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
