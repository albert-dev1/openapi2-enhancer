package de;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@picocli.CommandLine.Command(
        name = "openapi-enhancer-cli",
        description = "Downloads Openapi V2 Spec from Drupal and optimizes it."
)
public class OpenApiEnhancerCli implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        OpenApiEnhancer.processOpenApiSpec("", "openapi_out.json", "", "");

        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new OpenApiEnhancerCli()).execute(args);
        System.exit(exitCode);
    }

}
