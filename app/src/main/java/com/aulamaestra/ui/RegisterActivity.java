package com.aulamaestra.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aulamaestra.R;
import com.aulamaestra.api.dto.StudentJoinResponse;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.db.SessionManager;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {
    private final AulaRepository repo = AulaRepository.get();
    private SessionManager session;
    private View studentCodeBox;
    private TextInputEditText inputRegUser;
    private TextInputEditText inputRegPass;
    private TextInputEditText inputRegCode;
    private boolean isTeacher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        session = new SessionManager(this);
        studentCodeBox = findViewById(R.id.studentCodeBox);
        inputRegUser = findViewById(R.id.inputRegUser);
        inputRegPass = findViewById(R.id.inputRegPass);
        inputRegCode = findViewById(R.id.inputRegCode);

        MaterialButtonToggleGroup roleToggle = findViewById(R.id.roleToggle);
        roleToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            isTeacher = checkedId == R.id.btnRoleTeacher;
            studentCodeBox.setVisibility(isTeacher ? View.GONE : View.VISIBLE);
        });
        isTeacher = false;

        findViewById(R.id.btnRegister).setOnClickListener(v -> doRegister());
        findViewById(R.id.btnGoLogin).setOnClickListener(v -> finish());
    }

    private void doRegister() {
        String name = textOf(inputRegUser);
        String pass = textOf(inputRegPass);
        if (name.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, R.string.login_fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isTeacher) {
            registerTeacher(name, pass);
        } else {
            String code = textOf(inputRegCode);
            if (code.isEmpty()) {
                Toast.makeText(this, R.string.student_code_required, Toast.LENGTH_SHORT).show();
                return;
            }
            registerStudent(name, pass, code);
        }
    }

    private void registerTeacher(String name, String pass) {
        repo.registerTeacher(name, pass, new RepoCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                session.clearStudent();
                session.setTeacherId(id);
                Toast.makeText(RegisterActivity.this, R.string.register_ok, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this, TeacherSalonsActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void registerStudent(String name, String pass, String code) {
        repo.registerStudent(name, pass, code, new RepoCallback<StudentJoinResponse>() {
            @Override
            public void onSuccess(StudentJoinResponse r) {
                session.clearTeacher();
                session.saveStudentSession(r.studentId, r.displayName, r.salonId);
                Intent i = new Intent(RegisterActivity.this, StudentSalonActivity.class);
                i.putExtra(StudentSalonActivity.EXTRA_SALON_ID, r.salonId);
                i.putExtra(StudentSalonActivity.EXTRA_STUDENT_ID, r.studentId);
                startActivity(i);
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
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
