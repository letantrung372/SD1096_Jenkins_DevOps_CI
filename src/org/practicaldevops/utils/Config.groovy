package org.practicaldevops.utils

@Grab('org.yaml.snakeyaml:snakeyaml-engine:2.8')
import org.snakeyaml.engine.v2.YAML

class Config implements Serializable {
    private final def script
    private final def environment
    private def config
    
    Config(script, environment) {
        this.script = script
        this.environment = environment
        loadConfig()
    }
    
    private void loadConfig() {
        def configPath = "resources/config/${environment}.yaml"
        def configText = script.libraryResource(configPath)
        def yaml = new YAML()
        config = yaml.load(configText)
    }
    
    def getAWSRegion() {
        return config.aws.region
    }
    
    def getECRRepository() {
        return config.aws.ecrRepository
    }
    
    def getAWSAccountId() {
        return config.aws.accountId
    }
    
    def getServiceConfig(String serviceType) {
        return config.services[serviceType]
    }
    
    def getKubernetesNamespace() {
        return config.kubernetes.namespace
    }
}