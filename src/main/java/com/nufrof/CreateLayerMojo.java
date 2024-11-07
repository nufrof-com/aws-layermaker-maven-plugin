package com.nufrof;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Mojo(
        name = "create-layer",
        defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CreateLayerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    public void execute() throws MojoExecutionException {
        try {
            Set<Artifact> artifacts = project.getArtifacts();
            Set<File> providedArtifactFiles = new HashSet<>();

            for (Artifact artifact : artifacts) {
                if ("provided".equals(artifact.getScope())) {
                    File file = artifact.getFile();
                    if (file != null && file.exists()) {
                        providedArtifactFiles.add(file);
                    } else {
                        getLog().warn("Artifact file not found: " + artifact);
                    }
                }
            }

            if (providedArtifactFiles.isEmpty()) {
                getLog().info("No provided scope dependencies found.");
                return;
            }

            // Prepare the zip file
            File zipFile = new File(project.getBuild().getDirectory(),
                    project.getArtifactId() + "-" + project.getVersion() + "-layer.zip");

            zipDependencies(providedArtifactFiles, zipFile);

            // Attach the zip file as an artifact
            projectHelper.attachArtifact(project, "zip", "layer", zipFile);

            getLog().info("Provided dependencies zipped and attached as artifact: " + zipFile.getName());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to zip provided dependencies", e);
        }
    }

    private void zipDependencies(Set<File> files, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File file : files) {
                if (file != null && file.exists()) {
                    ZipEntry zipEntry = new ZipEntry("java/lib/" + file.getName());
                    zos.putNextEntry(zipEntry);

                    try (InputStream is = new FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }

                    zos.closeEntry();
                } else {
                    getLog().warn("File not found: " + file);
                }
            }
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
        }
    }
}
