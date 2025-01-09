package org.practicaldevops.templates
import org.practicaldevops.utils.Config

abstract class PipelineTemplate implements Serializable {
    protected def script
    protected def config
    protected def environment
    protected Config envConfig
    
    PipelineTemplate(script, config) {
        this.script = script
        this.config = config
        this.environment = config.environment ?: 'dev'
        this.envConfig = new Config(script, environment)
    }
    
    // Template method that defines the pipeline structure
    void execute() {
        script.pipeline {
            agent any
            
            environment {
                AWS_REGION = envConfig.getAWSRegion()
                ECR_REGISTRY = "${envConfig.getAWSAccountId()}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                ECR_REPO = envConfig.getECRRepository()
                IMAGE_NAME = "${ECR_REGISTRY}/${ECR_REPO}:${config.serviceType}-${environment}-${script.BUILD_NUMBER}"
                IMAGE_LATEST = "${ECR_REGISTRY}/${ECR_REPO}:${config.serviceType}-${environment}-latest"
                KUBERNETES_NAMESPACE = envConfig.getKubernetesNamespace()
                AWS_CREDENTIALS = script.credentials('aws-credentials')
            }
            
            stages {
                stage('Checkout') {
                    steps {
                        script {
                            checkout()
                        }
                    }
                }
                
                stage('Build') {
                    steps {
                        script {
                            build()
                        }
                    }
                }
                
                stage('Test') {
                    steps {
                        script {
                            test()
                        }
                    }
                }
                
                stage('Configure AWS') {
                    steps {
                        script {
                            configureAWS()
                        }
                    }
                }
                
                stage('Build Docker Image') {
                    steps {
                        script {
                            buildDocker()
                        }
                    }
                }
                
                stage('Push to ECR') {
                    steps {
                        script {
                            pushToECR()
                        }
                    }
                }
                
                stage('Deploy') {
                    steps {
                        script {
                            deploy()
                        }
                    }
                }
            }
            
            post {
                always {
                    script {
                        cleanup()
                    }
                }
            }
        }
    }
    
    // Abstract methods to be implemented by concrete classes
    abstract void checkout()
    abstract void build()
    abstract void test()
    abstract void buildDocker()
    
    // Common methods that can be overridden if needed
    void configureAWS() {
        script.withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', 
                               credentialsId: 'aws-credentials',
                               accessKeyVariable: 'AWS_ACCESS_KEY_ID', 
                               secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
            script.sh """
                aws configure set aws_access_key_id ${script.AWS_ACCESS_KEY_ID}
                aws configure set aws_secret_access_key ${script.AWS_SECRET_ACCESS_KEY}
                aws configure set default.region ${script.AWS_REGION}
                
                aws ecr get-login-password --region ${script.AWS_REGION} | \
                docker login --username AWS --password-stdin ${script.ECR_REGISTRY}
            """
        }
    }
    
    void pushToECR() {
        script.sh """
            aws ecr describe-repositories --repository-names ${config.serviceName} || \
            aws ecr create-repository --repository-name ${config.serviceName}
            
            docker push ${script.ECR_REGISTRY}/${config.serviceName}:${script.BUILD_NUMBER}
            docker push ${script.ECR_REGISTRY}/${config.serviceName}:latest
            
            docker rmi ${script.ECR_REGISTRY}/${config.serviceName}:${script.BUILD_NUMBER}
            docker rmi ${script.ECR_REGISTRY}/${config.serviceName}:latest
        """
    }
    
    void deploy() {
        def serviceConfig = envConfig.getServiceConfig(config.serviceType)
        
        script.withKubeConfig([credentialsId: 'kubernetes-credentials']) {
            script.sh """
                kubectl -n ${KUBERNETES_NAMESPACE} set image deployment/${config.serviceName} \
                ${config.serviceName}=${script.IMAGE_NAME}
                
                kubectl -n ${KUBERNETES_NAMESPACE} patch deployment ${config.serviceName} \
                --patch '{
                    "spec": {
                        "template": {
                            "spec": {
                                "containers": [{
                                    "name": "${config.serviceName}",
                                    "resources": {
                                        "requests": {
                                            "memory": "${serviceConfig.memory}",
                                            "cpu": "${serviceConfig.cpu}"
                                        },
                                        "limits": {
                                            "memory": "${serviceConfig.memory}",
                                            "cpu": "${serviceConfig.cpu}"
                                        }
                                    }
                                }]
                            }
                        }
                    }
                }'
            """
        }
    }
    
    void cleanup() {
        script.cleanWs()
    }
}