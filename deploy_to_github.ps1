param(
    [Parameter(Mandatory = $true)]
    [string]$RepoName,

    [string]$Owner = "",

    [ValidateSet('public','private')]
    [string]$Visibility = 'public'
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git is not installed. Install Git first, then re-run this script."
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Warning "GitHub CLI is not installed. You can still create the repo manually on GitHub and then run the final git commands."
    Write-Host "Manual steps:"
    Write-Host "  1) Create a GitHub repo at https://github.com/new"
    Write-Host "  2) Run: git init"
    Write-Host "  3) Run: git branch -M main"
    Write-Host "  4) Run: git add ."
    Write-Host "  5) Run: git commit -m 'Initial commit'"
    Write-Host "  6) Run: git remote add origin https://github.com/<OWNER>/<REPO>.git"
    Write-Host "  7) Run: git push -u origin main"
    exit 0
}

$repoFullName = if ($Owner) { "$Owner/$RepoName" } else { $RepoName }

if (-not (gh auth status 2>$null)) {
    throw "GitHub is not logged in. Run 'gh auth login' and then retry."
}

if (-not (Test-Path .git)) {
    git init
}

git checkout -B main

git add .

git commit -m "Initial commit" 2>$null | Out-Null

$existingRemote = git remote get-url origin 2>$null
if (-not $existingRemote) {
    gh repo create $repoFullName --$Visibility --source=. --remote=origin --push
} else {
    Write-Host "Remote already exists: $existingRemote"
    Write-Host "Pushing to GitHub..."
    git push -u origin main
}

Write-Host "Repository published successfully to GitHub: https://github.com/$repoFullName"
