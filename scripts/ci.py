#!/usr/bin/env python3
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0

import argparse
import os
import subprocess
import shlex

GIT_ORIGIN_REFIX = "refs/heads/"
VERBOSE = False


def vprint(message):
    global VERBOSE
    if VERBOSE:
        print(message)


def running_in_github_action():
    """
    Test if currently running in a GitHub action or running locally
    :return: True if running in GH, False otherwise
    """
    return "GITHUB_WORKFLOW" in os.environ


def shell(command, cwd=None, check=True):
    """
    Run a command
    :param command: command to run
    :param cwd: the current working directory to change to before executing the command
    :param check: flag indicating if the status code should be checked. When true an exception will be
    thrown if the command exits with a non-zero exit status.
    :returns: the subprocess CompletedProcess output
    """
    if not shell:
        command = shlex.split(command)

    vprint(f"running `{command}`")
    return subprocess.run(command, shell=True, check=check, cwd=cwd, capture_output=True)


def get_current_branch(repo_dir):
    """
    Get the current branch name for the repository rooted at repo_dir
    """
    pout = shell("git branch --show-current", cwd=repo_dir)
    return pout.stdout.decode("utf-8").strip()


def has_remote_branch(repo_dir, branch_name):
    """
    Check if the repository rooted at repo_dir has the given branch name. Returns true if it does, false otherwise.
    """
    pout = shell(f"git ls-remote --exit-code origin {GIT_ORIGIN_REFIX}{branch_name}", cwd=repo_dir, check=False)
    return pout.returncode == 0


def update_repo(repo_dir, branch_name):
    """
    Attempt to update the repository to the given branch name, if it exists.
    Otherwise tries the branch name set by `GITHUB_BASE_REF`, if it exists.
    """
    branch_name = branch_name.removeprefix(GIT_ORIGIN_REFIX)
    curr_branch = get_current_branch(repo_dir)
    vprint(f"current branch of `{repo_dir}`: {curr_branch}")
    if curr_branch == branch_name:
        vprint("branches match already, nothing to do")
        return

    base_branch = os.environ.get("GITHUB_BASE_REF")

    if has_remote_branch(repo_dir, branch_name):
        vprint(f"repo has target branch {branch_name}...updating")
        pout = shell(f"git switch {branch_name}", cwd=repo_dir)
        vprint(pout.stdout.decode("utf-8"))
    elif (base_branch != curr_branch) and has_remote_branch(repo_dir, base_branch):
        vprint(f"repo does not have target branch ${branch_name}, but has base branch {base_branch}...updating")
        pout = shell(f"git switch {base_branch}", cwd=repo_dir)
        vprint(pout.stdout.decode("utf-8"))
    else:
        vprint(f"repo does not have target branch {branch_name} nor base_branch {base_branch}, leaving at {curr_branch}")

def _get_branch_cmd(opts):
    branch = "main"
    if running_in_github_action():
        gh_head_ref = os.environ.get("GITHUB_HEAD_REF")
        gh_ref = os.environ.get("GITHUB_REF")
        if gh_head_ref:
            branch = gh_head_ref
        elif gh_ref:
            branch = gh_ref.removeprefix(GIT_ORIGIN_REFIX)
    else:
        branch = get_current_branch(opts.repository)

    print(branch)


def _set_branch_cmd(opts):
    update_repo(opts.repository, opts.branch)


def _checkout_pr_cmd(opts):
    vprint(f"checking if PR #{opts.pr} exists for {opts.repository}")
    try:
        shell(f"git ls-remote origin 'pull/*/head' | grep 'refs/pull/{opts.pr}/head'")
    except subprocess.CalledProcessError as error:
        print(f"PR #{opts.pr} does not exist. Please specify a valid PR number. {error}")
        exit(1)
    vprint(f"PR #{opts.pr} exists. Checking out PR.")
    shell(f"git fetch origin pull/{opts.pr}/head:pr-{opts.pr}")
    shell(f"git switch pr-{opts.pr}")


def create_cli():
    parser = argparse.ArgumentParser(
        prog="ci",
        description="Utilities for setting up dependencies correctly in CI",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )

    parser.add_argument("-v", "--verbose", help="enable verbose output", action="store_true")
    subparsers = parser.add_subparsers()
    set_branch = subparsers.add_parser('set-branch', help="update a local git repository to a given branch if it exists on the remote")
    get_branch = subparsers.add_parser('get-branch', help="get the current branch of a local git repository")
    checkout_pr = subparsers.add_parser('checkout-pr', help="checks out a pr if it exists, throws error otherwise")

    get_branch.add_argument("repository", nargs="?", help="the local repository directory to update", default=os.getcwd())
    get_branch.set_defaults(cmd=_get_branch_cmd)

    set_branch.add_argument("--branch", help="the name of the branch to sync to if it exists", required=True)
    set_branch.add_argument("repository", nargs="?", help="the local repository directory to update", default=os.getcwd())
    set_branch.set_defaults(cmd=_set_branch_cmd)

    checkout_pr.add_argument("--pr", help="the pr number to use", required=True)
    checkout_pr.add_argument("repository", nargs="?", help="the local repository directory to update", default=os.getcwd())
    checkout_pr.set_defaults(cmd=_checkout_pr_cmd)

    return parser


def main():
    cli = create_cli()
    opts = cli.parse_args()
    opts.repository = os.path.abspath(opts.repository)
    if opts.verbose:
        global VERBOSE
        VERBOSE = True
    opts.cmd(opts)


if __name__ == '__main__':
    main()
