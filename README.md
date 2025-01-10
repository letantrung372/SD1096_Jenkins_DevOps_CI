### Jenkins Shared Library
#### Configuration
1. AWS Credentials
2. GitHub Personal Access Token (PAT)
#### Pipeline Structure:
```
jenkins-shared-library/                 # Shared Library Repository
├── vars/
│   ├── backendServicePipeline.groovy   # Script for backend pipeline
│   └── frontendServicePipeline.groovy  # Script for frontend pipeline
└── src/
    └── org/practicaldevops/
        └── Global.groovy               # Global class contains reusable functions
```

#### Pipeline Stages
1. AWS Authentication & ECR Login
2. Build Docker Image
3. Tag & Push to ECR
4. Deploy to EKS
