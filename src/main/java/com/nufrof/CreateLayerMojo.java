package com.nufrof;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

// Import Maven model classes

// Import Aether classes
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;



@Mojo(name = "create-layer", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class CreateLayerMojo extends AbstractMojo {

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
            // Prepare the collect request for dependencies
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRepositories(project.getRemoteProjectRepositories());

            // Add dependencies with "provided" scope to the collect request
            for (org.apache.maven.model.Dependency dependency : project.getDependencies()) {
                if ("provided".equals(dependency.getScope())) {
                    collectRequest.addDependency(new org.eclipse.aether.graph.Dependency(
                            new DefaultArtifact(
                                    dependency.getGroupId(),
                                    dependency.getArtifactId(),
                                    dependency.getClassifier(),
                                    dependency.getType(),
                                    dependency.getVersion()
                            ),
                            dependency.getScope()
                    ));
                }
            }

            if (collectRequest.getDependencies().isEmpty()) {
                getLog().info("No provided scope dependencies found.");
                return;
            }

            // Define a dependency filter to include only "provided" scope dependencies
            DependencyFilter dependencyFilter = DependencyFilterUtils.classpathFilter("provided");

            // Resolve dependencies
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, dependencyFilter);
            DependencyResult dependencyResult = repoSystem.resolveDependencies(
                    session.getRepositorySession(), dependencyRequest
            );

            // Generate a list of all resolved artifacts
            PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
            dependencyResult.getRoot().accept(nlg);

            Set<File> providedArtifactFiles = new HashSet<>();
            for (org.eclipse.aether.artifact.Artifact aetherArtifact : nlg.getArtifacts(false)) {
                File artifactFile = aetherArtifact.getFile();
                if (artifactFile != null && artifactFile.exists()) {
                    providedArtifactFiles.add(artifactFile);
                } else {
                    getLog().warn("Artifact file not found: " + aetherArtifact);
                }
            }

            if (providedArtifactFiles.isEmpty()) {
                getLog().info("No provided scope dependencies found after resolution.");
                return;
            }

            // Prepare the zip file
            File zipFile = new File(project.getBuild().getDirectory(),
                    project.getArtifactId() + "-" + project.getVersion() + "-layer.zip");

            zipDependencies(providedArtifactFiles, zipFile);

            // Attach the zip file as an artifact
            projectHelper.attachArtifact(project, "zip", "layer", zipFile);

            getLog().info("Provided dependencies (including transitives) zipped and attached as artifact: " + zipFile.getName());
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
