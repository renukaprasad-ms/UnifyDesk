# Backend Snowflake Test

You can run the local k3s flow from the root helper:

```sh
chmod +x k8s.sh
./k8s.sh start
./k8s.sh logs backend 200
./k8s.sh describe backend
./k8s.sh top
./k8s.sh top 5
./k8s.sh top 1 once
./k8s.sh restart
./k8s.sh stop
./k8s.sh delete
```

This runs 3 backend pods with unique Snowflake node IDs from the StatefulSet ordinal:

- `messaging-backend-0` -> `SNOWFLAKE_NODE_ID=0`
- `messaging-backend-1` -> `SNOWFLAKE_NODE_ID=1`
- `messaging-backend-2` -> `SNOWFLAKE_NODE_ID=2`

Build/load the image into your cluster first:

```sh
docker build -t messaging-backend:latest ./backend
docker build -t messaging-frontend:latest --build-arg VITE_API_BASE_URL=http://localhost:8001 ./frontend
```

For minikube:

```sh
minikube image load messaging-backend:latest
minikube image load messaging-frontend:latest
```

Apply the manifest:

```sh
kubectl create secret generic messaging-env --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/dependencies.yaml
kubectl apply -f k8s/backend.yaml
kubectl apply -f k8s/frontend.yaml
kubectl get pods -l app=messaging-backend
kubectl logs messaging-backend-0
kubectl logs messaging-backend-1
kubectl logs messaging-backend-2
```

If Kubernetes rejects the backend apply with an immutable `StatefulSet` field error, recreate the StatefulSet:

```sh
kubectl delete statefulset messaging-backend
kubectl apply -f k8s/backend.yaml
kubectl get pods -l app=messaging-backend
```

If old dependency pods remain from previous rollout attempts, recreate the dependency Deployments:

```sh
kubectl delete deployment postgres redis kafka
kubectl delete pod -l app=postgres --ignore-not-found
kubectl delete pod -l app=redis --ignore-not-found
kubectl delete pod -l app=kafka --ignore-not-found
kubectl apply -f k8s/dependencies.yaml
```

Open the frontend through NodePort:

```sh
http://localhost:30100
```

If k3s is running inside WSL and Windows Chrome cannot reach `localhost:30100`, use the WSL IP:

```sh
hostname -I | awk '{print $1}'
```

Then open:

```sh
http://<wsl-ip>:30100
```

Forward the backend service when you need direct API access:

```sh
kubectl port-forward svc/messaging-backend 8001:8001
```

Resource limits:

- backend: 2 CPU, 1Gi memory
- kafka: 2 CPU, 1Gi memory
- postgres: 1 CPU, 500Mi memory
- redis: 500m CPU, 500Mi memory
- frontend: 250m CPU, 128Mi memory
