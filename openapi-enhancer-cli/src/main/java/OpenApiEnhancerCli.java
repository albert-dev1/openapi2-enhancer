import picocli.CommandLine;

import java.util.concurrent.Callable;

@picocli.CommandLine.Command(
        name = "run",
        description = "Downloads Openapi V2 Spec from Drupal and optimizes it.",
        mixinStandardHelpOptions = true,
        version = "openapi-enhancer-cli 1.0"
)
public class OpenApiEnhancerCli implements Callable<Integer> {
    @CommandLine.Option(names = {"-i", "--inputSpec"})
    private String inputSpec;

    @CommandLine.Option(names = {"-o", "--outputSpec"})
    private String outputSpec;

    @CommandLine.Option(names = {"-u", "--user"})
    private String username;

    @CommandLine.Option(names = {"-p", "--password"})
    private String password;

    @Override
    public Integer call() throws Exception {
        OpenApiEnhancer.processOpenApiSpec(inputSpec, outputSpec, username, password);
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new OpenApiEnhancerCli()).execute(args);
        System.exit(exitCode);
    }

}
