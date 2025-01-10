#!/usr/bin/env groovy
import org.practicaldevops.*

void call(Map pipelineParams) {
    def config = [
        AWS: [
            REGION: 'ap-southeast-1',
            ACCOUNT_ID: '307946653621'
        ],
        SERVICE: [
            NAME: 'frontend',
            CONTAINER_NAME: 'frontend-container',
            ECR_REPOSITORY: 'dev-frontend'
        ],
        KUBERNETES: [
            CLUSTER_NAME: 'dev-msa-cluster',
            NAMESPACE: 'msa-application-namespace',
            DEPLOYMENT_NAME: 'frontend-development'
        ],
        GIT: [
            REPO_NAME: 'SD1096_MSA_GitOps'
        ]
    ]

    // Derived values
    def ECR_REGISTRY = "${config.AWS.ACCOUNT_ID}.dkr.ecr.${config.AWS.REGION}.amazonaws.com"
    def IMAGE_TAG = "${config.SERVICE.NAME}.${BUILD_NUMBER}-${new Date().format('yyyyMMddHHmmss')}"

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
            stage('AWS Authentication & ECR Login') {
                steps {
                    script{
                        withAWS(credentials: 'aws-credentials', region: config.AWS.REGION) {
                            global.loginToECR(
                                AWS_REGION: config.AWS.REGION,
                                ECR_REGISTRY: ECR_REGISTRY
                            )
                        }
                    }
                }
            }

            stage('Build Docker Image') {
                steps {
                    script{
                        global.buildDockerImages(
                            ECR_REPOSITORY: config.SERVICE.ECR_REPOSITORY,
                            IMAGE_TAG: IMAGE_TAG
                        )
                    }
                }
            }

            stage('Tag & Push to ECR') {
                steps {
                    script {
                        global.tagDockerImages(
                            ECR_REPOSITORY: config.SERVICE.ECR_REPOSITORY,
                            IMAGE_TAG: IMAGE_TAG
                        )
                        global.pushDockerImages(
                            ECR_REPOSITORY: config.SERVICE.ECR_REPOSITORY,
                            IMAGE_TAG: IMAGE_TAG
                        )
                    }
                }
            }

            stage('Deploy to EKS') {
                steps {
                    script {
                        // Update GitOps repository
                        global.updateGitOpsRepo(
                            SERVICE_NAME:config.SERVICE.NAME,
                            ECR_REGISTRY:ECR_REGISTRY,
                            ECR_REPOSITORY:config.SERVICE.ECR_REPOSITORY,
                            IMAGE_TAG: IMAGE_TAG,
                        )

                        // Deploy to EKS
                        withAWS(credentials: 'aws-credentials', region: config.AWS.REGION) {
                            global.deployToEKS(
                                CLUSTER_NAME: config.KUBERNETES.CLUSTER_NAME,
                                NAMESPACE: config.KUBERNETES.NAMESPACE,
                                DEPLOYMENT_NAME: config.KUBERNETES.DEPLOYMENT_NAME,
                                ECR_REPOSITORY: config.SERVICE.ECR_REPOSITORY,
                                IMAGE_TAG: IMAGE_TAG,
                                CONTAINER_NAME: config.SERVICE.CONTAINER_NAME,
                                SERVICE_NAME: config.SERVICE.NAME
                            )
                        }
                    }
                }
            }
        }

        post {
            always {
                echo 'Pipeline completed.'
            }
            cleanup {
                cleanWs()
            }
        }
    }
}
