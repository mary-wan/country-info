#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Builds the image, makes it available to the target cluster and applies the manifests.

.DESCRIPTION
    Works against any Kubernetes cluster. The image is made available in one of three ways:

      -Registry <host/project>  build, tag and push to a container registry (remote clusters)
      -LoadInto <tool>          load the local image straight into a local cluster
                                (k3d, kind or minikube), no registry needed
      neither                   assume the image is already available to the cluster

.EXAMPLE
    .\deploy.ps1 -Registry docker.io/myuser -Tag v1
    .\deploy.ps1 -LoadInto k3d -ClusterName country-info
    .\deploy.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    [string]$Tag = "latest",
    [string]$Registry,
    [ValidateSet("k3d", "kind", "minikube")]
    [string]$LoadInto,
    [string]$ClusterName = "country-info",
    [string]$Namespace = "country-info",
    [string]$Context,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$image = if ($Registry) { "$Registry/country-information:$Tag" } else { "country-information:$Tag" }
$manifests = Join-Path $PSScriptRoot "k8s"

if ($Context) {
    Write-Host "==> Using context '$Context'" -ForegroundColor Cyan
    kubectl config use-context $Context
    if ($LASTEXITCODE -ne 0) { throw "unknown context '$Context'" }
}

Write-Host "==> Target: $(kubectl config current-context) / namespace $Namespace" -ForegroundColor Cyan

if (-not $SkipBuild) {
    Write-Host "==> Building $image" -ForegroundColor Cyan
    docker build -t $image $PSScriptRoot
    if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

    if ($Registry) {
        Write-Host "==> Pushing $image" -ForegroundColor Cyan
        docker push $image
        if ($LASTEXITCODE -ne 0) { throw "docker push failed" }
    }
    elseif ($LoadInto) {
        Write-Host "==> Loading $image into the local $LoadInto cluster '$ClusterName'" -ForegroundColor Cyan
        switch ($LoadInto) {
            "k3d" { k3d image import $image --cluster $ClusterName }
            "kind" { kind load docker-image $image --name $ClusterName }
            "minikube" { minikube image load $image --profile $ClusterName }
        }
        if ($LASTEXITCODE -ne 0) { throw "failed to load the image into the cluster" }
    }
    else {
        Write-Host "    No -Registry or -LoadInto given; assuming the cluster can already resolve $image" -ForegroundColor Yellow
    }
}

Write-Host "==> Applying manifests" -ForegroundColor Cyan
$ordered = @(
    "namespace.yml",
    "logback-configmap.yml",
    "configmap.yml",
    "app-secret.yml",
    "app-deployment.yml",
    "app-service.yml",
    "app-ingress.yml",
    "hpa.yml"
)
foreach ($file in $ordered) {
    kubectl apply -f (Join-Path $manifests $file)
    if ($LASTEXITCODE -ne 0) { throw "failed to apply $file" }
}

Write-Host "==> Setting image to $image" -ForegroundColor Cyan
kubectl set image deployment/country-information -n $Namespace country-information=$image
if ($LASTEXITCODE -ne 0) { throw "failed to set the container image" }

Write-Host "==> Waiting for rollout" -ForegroundColor Cyan
kubectl rollout status deployment/country-information -n $Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) {
    Write-Host "Rollout failed. Recent pod state and logs:" -ForegroundColor Red
    kubectl get pods -n $Namespace
    kubectl logs -n $Namespace -l app=country-information --tail=50
    throw "rollout did not complete"
}

Write-Host "==> Deployed" -ForegroundColor Green
kubectl get pods,svc,ingress,hpa -n $Namespace
