#!/usr/bin/env groovy
import org.practicaldevops.*

void call(Map pipelineParams) {
    def AWS_REGION = 'ap-southeast-1'
    def ECR_REPO = 'practical-devops-ecr'
    def AWS_ACCOUNTID = '307946653621'
    def serviceName = 'backend'
    def ECR_REGISTRY = "${AWS_ACCOUNTID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    def IMAGE_TAG = "SD1096-${serviceName}.${BUILD_NUMBER}-${new Date().format('yyyyMMddHHmmss')}"
    def CONTAINER_NAME = "${serviceName}-container"
    def CLUSTER_NAME = 'practical-devops-eks'
    def NAMESPACE = "${serviceName}-ns"
    def DEPLOYMENT_NAME = "${serviceName}-development"
    def ECR_REPOSITORY = "${ECR_REPO}"

    def global = new Global()

    pipeline {
        agent any

        triggers {
            githubPush()
        }

        options {
            disableConcurrentBuilds()
            disableResume()
            timeout(time: 1, unit: 'HOURS')
        }

        stages {
            stage('Login to AWS ECR') {
                steps {
                    script {
                        withAWS(credentials: 'AWSCredentails', region: AWS_REGION) {
                            sh """
                                aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                            """
                        }
                    }
                }
            }

            stage('Build Docker Image') {
                steps {
                    script {
                        dir('src/backend') {
                            sh """
                                docker build -t ${ECR_REPO}:${IMAGE_TAG} .
                            """
                        }
                    }
                }
            }

            stage('Tag Docker Image') {
                steps {
                    script {
                        sh """
                            docker tag ${ECR_REPO}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}
                        """
                    }
                }
            }

            stage('Push to ECR') {
                steps {
                    script {
                        sh """
                            docker push ${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}
                        """
                    }
                }
            }

            stage('Deploy to EKS') {
                steps {
                    script {
                        // Step 1: Clone the Git repository that contains the Kubernetes manifests
                        withCredentials([string(credentialsId: 'github-pat', variable: 'GITHUB_TOKEN')]) {
                            sh """
                            # Clean any existing directory
                            rm -rf SD1096_MSA_GitOps

                            git clone https://${GITHUB_TOKEN}@github.com/letantrung372/SD1096_MSA_GitOps.git
                            cd SD1096_MSA_GitOps/${serviceName}

                            # Update the deployment.yaml with the new image tag
                                sed -i 's|image: .*|image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}|g' deployment.yaml

                                # Commit the changes to Git
                                git config user.name "Jenkins CI"
                                git config user.email "jenkins@your-domain.com"
                                git add deployment.yaml
                                git commit -m "Update deployment image to ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
                                git push -f origin master  # Or use your relevant branch
                        """
                        }

                        // Ensure that you are using the correct AWS credentials and region
                        withAWS(credentials: 'AWSCredentails', region: AWS_REGION) {
                            // Ensure the deployToEKS function is available in the global context

                            global.deployToEKS(
                                CLUSTER_NAME: CLUSTER_NAME,
                                NAMESPACE: NAMESPACE,
                                DEPLOYMENT_NAME: DEPLOYMENT_NAME,
                                ECR_REPOSITORY: ECR_REPOSITORY,
                                IMAGE_TAG: IMAGE_TAG,
                                CONTAINER_NAME: CONTAINER_NAME,
                                SERVICE_NAME:serviceName
                )
                        }
                    }
                }
            }
        }

        post {
            cleanup {
                cleanWs()
            }
        }
    }
}
