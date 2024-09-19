package com.nufrof;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Mojo(name = "zip-provided-dependencies", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ZipProvidedDependenciesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private RepositorySystem repoSystem;

    public void execute() throws MojoExecutionException {
        try {
            // Collect provided scope dependencies
            List<Dependency> dependencies = project.getDependencies();
            Set<File> providedArtifactFiles = new HashSet<>();

            for (Dependency dependency : dependencies) {
                if ("provided".equals(dependency.getScope())) {
                    // Create a DefaultArtifact instance
                    org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(
                            dependency.getGroupId(),
                            dependency.getArtifactId(),
                            dependency.getClassifier(),
                            dependency.getType(),
                            dependency.getVersion()
                    );

                    // Prepare the artifact request
                    ArtifactRequest artifactRequest = new ArtifactRequest();
                    artifactRequest.setArtifact(aetherArtifact);
                    artifactRequest.setRepositories(project.getRemoteProjectRepositories());

                    // Resolve the artifact
                    ArtifactResult artifactResult = repoSystem.resolveArtifact(
                            session.getRepositorySession(), artifactRequest
                    );

                    File artifactFile = artifactResult.getArtifact().getFile();
                    if (artifactFile != null && artifactFile.exists()) {
                        providedArtifactFiles.add(artifactFile);
                    } else {
                        getLog().warn("Artifact file not found for dependency: " + dependency);
                    }
                }
            }

            if (providedArtifactFiles.isEmpty()) {
                getLog().info("No provided scope dependencies found.");
                return;
            }

            // Prepare the zip file
            File zipFile = new File(project.getBuild().getDirectory(),
                    project.getArtifactId() + "-provided-dependencies.zip");

            zipDependencies(providedArtifactFiles, zipFile);

            // Attach the zip file as an artifact
            projectHelper.attachArtifact(project, "zip", "provided-dependencies", zipFile);

            getLog().info("Provided dependencies zipped and attached as artifact: " + zipFile.getName());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to zip provided dependencies", e);
        }
    }


    private void zipDependencies(Set<File> files, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File file : files) {
                if (file != null && file.exists()) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
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
        }
    }
}
