package com.ai.service.impl;

import com.ai.service.IntegrationCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceImplTest {

    @Mock
    private IntegrationCredentialsService credentials;

    @TempDir
    Path temp;

    private ImageUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImageUploadServiceImpl();
        ReflectionTestUtils.setField(service, "credentials", credentials);
        when(credentials.uploadPath()).thenReturn(temp.toString());
    }

    @Test
    void deleteImage_rejectsPathTraversal() throws Exception {
        Files.createDirectories(temp.resolve("1"));
        Path secret = temp.resolve("secret.txt");
        Files.writeString(secret, "x");
        assertFalse(service.deleteImage("../../secret.txt", 1L));
        assertFalse(service.deleteImage("..\\secret.txt", 1L));
        assertTrue(Files.exists(secret));
    }

    @Test
    void deleteImage_rejectsCrossUserDirectory() throws Exception {
        Files.createDirectories(temp.resolve("1"));
        Path otherFile = Files.createDirectories(temp.resolve("2")).resolve("pic.jpg");
        Files.writeString(otherFile, "x");
        assertFalse(service.deleteImage("../2/pic.jpg", 1L));
        assertTrue(Files.exists(otherFile));
    }

    @Test
    void deleteImage_deletesOwnBasename() throws Exception {
        Path file = Files.createDirectories(temp.resolve("1")).resolve("20260101000000_1.jpg");
        Files.writeString(file, "x");
        assertTrue(service.deleteImage("20260101000000_1.jpg", 1L));
        assertFalse(Files.exists(file));
    }
}
