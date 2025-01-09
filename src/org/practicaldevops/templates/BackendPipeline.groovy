package org.practicaldevops.templates

class BackendPipeline extends PipelineTemplate {
    BackendPipeline(script, config) {
        super(script, config)
    }
    
    @Override
    void checkout() {
        script.checkout script.scm
    }
    
    @Override
    void build() {
        script.sh './gradlew clean build'
    }
    
    @Override
    void test() {
        script.sh '''
            ./gradlew test
            ./gradlew checkstyle
        '''
    }
    
    @Override
    void buildDocker() {
        script.sh """
            docker build -t ${script.ECR_REGISTRY}/${config.serviceName}:${script.BUILD_NUMBER} \
            -t ${script.ECR_REGISTRY}/${config.serviceName}:latest \
            -f Dockerfile.backend .
        """
    }
}