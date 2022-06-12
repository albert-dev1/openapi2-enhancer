package de;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Mojo(name = "openapi-v2-enhancer", defaultPhase = LifecyclePhase.COMPILE)
public class OpenAPIEnhancerMojo extends AbstractMojo {

    @Parameter(property = "inputSpec")
    String inputSpec;

    @Parameter(property = "outputSpec", defaultValue = "openapi_out.json")
    String outputSpec;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            OpenApiEnhancer.processOpenApiSpec(inputSpec, outputSpec);
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            e.printStackTrace();
        }
    }
}
