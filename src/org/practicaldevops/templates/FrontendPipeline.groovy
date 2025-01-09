package org.practicaldevops.templates

class FrontendPipeline extends PipelineTemplate {
    FrontendPipeline(script, config) {
        super(script, config)
    }
    
    @Override
    void checkout() {
        script.checkout script.scm
    }
    
    @Override
    void build() {
        script.sh '''
            npm install
            npm run build
        '''
    }
    
    @Override
    void test() {
        script.sh '''
            npm run test
            npm run lint
        '''
    }
    
    @Override
    void buildDocker() {
        script.sh """
            docker build -t ${script.ECR_REGISTRY}/${config.serviceName}:${script.BUILD_NUMBER} \
            -t ${script.ECR_REGISTRY}/${config.serviceName}:latest \
            -f Dockerfile.frontend .
        """
    }
}