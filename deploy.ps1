#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Builds the image, loads it into the k3d cluster and applies the manifests.
.EXAMPLE
    .\deploy.ps1
    .\deploy.ps1 -Tag v1 -SkipBuild
#>
[CmdletBinding()]
param(
    [string]$Tag = "latest",
    [string]$Cluster = "country-info",
    [string]$Namespace = "country-info",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$image = "country-information:$Tag"
$manifests = Join-Path $PSScriptRoot "deployment-manifests"

if (-not $SkipBuild) {
    Write-Host "==> Building $image" -ForegroundColor Cyan
    docker build -t $image $PSScriptRoot
    if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

    Write-Host "==> Importing $image into k3d cluster '$Cluster'" -ForegroundColor Cyan
    k3d image import $image --cluster $Cluster
    if ($LASTEXITCODE -ne 0) { throw "k3d image import failed" }
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

Write-Host "==> Waiting for rollout" -ForegroundColor Cyan
kubectl rollout status deployment/country-information -n $Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) {
    Write-Host "Rollout failed. Recent pod events:" -ForegroundColor Red
    kubectl get pods -n $Namespace
    kubectl logs -n $Namespace -l app=country-information --tail=50
    throw "rollout did not complete"
}

Write-Host "==> Deployed" -ForegroundColor Green
kubectl get pods,svc,ingress,hpa -n $Namespace
