from setuptools import find_packages, setup

with open("README.md", encoding="utf-8") as fh:
    long_description = fh.read()

setup(
    name="my-platform-client",
    version="1.0.0",
    description="OAuth2 client + message publish SDK for My Platform",
    long_description=long_description,
    long_description_content_type="text/markdown",
    author="My Platform Team",
    url="https://github.com/example/my-platform",
    packages=find_packages(exclude=("tests", "tests.*")),
    python_requires=">=3.8",
    install_requires=[
        "requests>=2.28",
    ],
    classifiers=[
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3 :: Only",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
    ],
)
