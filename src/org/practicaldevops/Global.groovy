package org.practicaldevops

// def pythonRunInstallDependencies(){
//     stage ("Run Install Dependencies ") {
//         sh "mkdir -p results"
//         sh 'docker run --rm -v $(pwd):/app python:3.9-slim bash -c "pip install poetry && cd /app && poetry config virtualenvs.in-project true && poetry install"'
//     }
// }

def loginToECR(args) {
    def AWS_REGION = args.AWS_REGION
    def ECR_REGISTRY = args.ECR_REGISTRY

    script {
                sh """
                aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                """
    }
}

def buildDockerImages(args) {
    def ECR_REPOSITORY = args.ECR_REPOSITORY
    def IMAGE_TAG = args.IMAGE_TAG

    script {
                sh """
                docker build -t ${ECR_REPOSITORY}:${IMAGE_TAG} .
                """
    }
}

def tagDockerImages(args) {
    def ECR_REPOSITORY = args.ECR_REPOSITORY
    def IMAGE_TAG = args.IMAGE_TAG

    script {
                sh """
                docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
                """
            }
}

def pushDockerImages(args) {
    def ECR_REPOSITORY = args.ECR_REPOSITORY
    def IMAGE_TAG = args.IMAGE_TAG

    script {
                sh """
                docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
                """
            }
}

def deployToEKS(args) {
    def CLUSTER_NAME = args.CLUSTER_NAME
    def NAMESPACE = "practical-devops-ns"
    // def DEPLOYMENT_NAME = args.DEPLOYMENT_NAME
    def ECR_REPOSITORY = args.ECR_REPOSITORY
    def IMAGE_TAG = args.IMAGE_TAG
    def CONTAINER_NAME = args.CONTAINER_NAME
    def ECR_REGISTRY = args.ECR_REGISTRY
    def SERVICE_NAME = args.SERVICE_NAME

    script {
                sh """
            cd SD1096_MSA_GitOps/${SERVICE_NAME}

            # Update kubeconfig
            aws eks update-kubeconfig --name ${CLUSTER_NAME}

            # Check if the namespace exists, and create it if it doesn't
            kubectl get namespace ${NAMESPACE} || kubectl create namespace ${NAMESPACE}

            # Apply the deployment YAML
            kubectl apply -f deployment.yaml --namespace ${NAMESPACE}

            # Get the actual deployment name from kubernetes
            ACTUAL_DEPLOYMENT_NAME=\$(kubectl get deployment -n ${NAMESPACE} -o jsonpath='{.items[0].metadata.name}')
            echo "Actual deployment name: \${ACTUAL_DEPLOYMENT_NAME}"

            # Update the container image using the actual deployment name
            kubectl set image deployment/\${ACTUAL_DEPLOYMENT_NAME} \
                ${CONTAINER_NAME}=${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG} \
                -n ${NAMESPACE}

            # Wait for rollout to complete
            #kubectl rollout status deployment/\${ACTUAL_DEPLOYMENT_NAME} -n ${NAMESPACE}
        """
            }
}
